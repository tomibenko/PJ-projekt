import os
import numpy as np
import struct
import time
from PIL import Image
from bitarray import bitarray
import math

def predict_values(channel, image_width, image_height):
    errors = np.zeros((image_height, image_width), dtype=np.int32)

    for y in range(image_height):
        for x in range(image_width):
            if y == 0 and x == 0:
                prediction = channel[0, 0]
            elif y == 0:
                prediction = channel[y, x - 1]
            elif x == 0:
                prediction = channel[y - 1, x]
            else:
                p_left = channel[y, x - 1]
                p_top = channel[y - 1, x]
                p_top_left = channel[y - 1, x - 1]

                if p_top_left >= max(p_left, p_top):
                    prediction = min(p_left, p_top)
                elif p_top_left <= min(p_left, p_top):
                    prediction = max(p_left, p_top)
                else:
                    prediction = p_left + p_top - p_top_left

            errors[y, x] = channel[y, x] - prediction

    return errors.flatten()

# Perform the inverse prediction based on JPEG-LS on a 2D channel.
def predict_inverse(errors, image_width, image_height):
    reconstructed = np.zeros((image_height, image_width), dtype=np.int32)

    for y in range(image_height):
        for x in range(image_width):
            idx = y * image_width + x
            if y == 0 and x == 0:
                reconstructed[y, x] = errors[0]
            elif y == 0:
                reconstructed[y, x] = reconstructed[y, x - 1] + errors[idx]
            elif x == 0:
                reconstructed[y, x] = reconstructed[y - 1, x] + errors[idx]
            else:
                p_left = reconstructed[y, x - 1]
                p_top = reconstructed[y - 1, x]
                p_top_left = reconstructed[y - 1, x - 1]

                if p_top_left >= max(p_left, p_top):
                    prediction = min(p_left, p_top)
                elif p_top_left <= min(p_left, p_top):
                    prediction = max(p_left, p_top)
                else:
                    prediction = p_left + p_top - p_top_left

                reconstructed[y, x] = prediction + errors[idx]

    return reconstructed

# Set the header for a single channel compressed block.
# Packing: image_width (H), image_height (H), c0 (I), cn (I), n (I)
def set_channel_header(image_width, image_height, c0, cn, n):
    header = struct.pack('>HHIII', image_height, image_width, c0, cn, n)
    return header

# Decode the header information for a channel from a file-like object.
def decode_channel_header(file):
    height, width, c0, cn, n = struct.unpack('>HHIII', file.read(16))
    return height, width, c0, cn, n

# Encode a value into the bitstream using fixed binary encoding.
def encode(bitstream, g, value):
    binary_value = format(value, f'0{g}b')
    bitstream.extend(binary_value)
    return bitstream

# Decode a value from the bitstream using fixed binary decoding.
def decode(bitstream, g):
    value = int(bitstream[:g].to01(), 2)
    del bitstream[:g]
    return value

# Perform interpolative coding on the cumulative values.
def ic(bitstream, C, l, h):
    if h - l > 1:
        if C[h] != C[l]:
            m = (h + l) // 2
            g = int(math.ceil(math.log2(C[h] - C[l] + 1)))
            bitstream = encode(bitstream, g, C[m] - C[l])

            if l < m:
                bitstream = ic(bitstream, C, l, m)
            if m < h:
                bitstream = ic(bitstream, C, m, h)
    return bitstream

# Perform interpolative decoding on the cumulative values.
def deic(bitstream, C, l, h):
    if h - l > 1:
        if C[h] == C[l]:
            for i in range(l + 1, h):
                C[i] = C[l]
        else:
            m = (h + l) // 2
            g = int(math.ceil(math.log2(C[h] - C[l] + 1)))
            C[m] = C[l] + decode(bitstream, g)

            if l < m:
                deic(bitstream, C, l, m)
            if m < h:
                deic(bitstream, C, m, h)

##########################################
# Compression and Decompression for Color Images
##########################################

def compress_color(image, output_path):
    """
    Compress a color image (3 channels) using channel-wise JPEG-LS prediction and interpolative coding.
    The file format will start with an overall header:
      - image height (H, 2 bytes)
      - image width (H, 2 bytes)
      - number of channels (B, 1 byte)
    Followed by, for each channel, a channel header (16 bytes) and the bitstream (the rest of the data for that channel).
    """
    # Ensure image is a numpy array and in RGB mode.
    if not isinstance(image, np.ndarray):
        image = np.array(image)
        
    if image.ndim != 3 or image.shape[2] != 3:
        raise ValueError("The provided image is not a color image with 3 channels.")

    height, width, channels = image.shape

    overall_header = struct.pack('>HHB', height, width, channels)
    with open(output_path, 'wb') as f:
        f.write(overall_header)

        # Process each channel separately.
        for ch in range(channels):
            channel = image[:, :, ch]
            errors = predict_values(channel, width, height)
            n = width * height

            # Map prediction errors to non-negative integers (using a similar scheme as before).
            N = np.zeros(n, dtype=np.int32)
            for i in range(n):
                if errors[i] >= 0:
                    N[i] = 2 * errors[i]
                else:
                    N[i] = 2 * abs(errors[i]) - 1

            # Compute cumulative sums.
            C = np.zeros(n, dtype=np.int32)
            C[0] = N[0]
            for i in range(1, n):
                C[i] = C[i - 1] + N[i]

            # Write channel header.
            channel_header = set_channel_header(width, height, C[0], C[-1], n)
            f.write(channel_header)

            # Create the bitstream using interpolative coding.
            bitstream = bitarray()
            bitstream = ic(bitstream, C, 0, n - 1)
            
            bitstream_bytes = bitstream.tobytes()
            stream_len = len(bitstream_bytes)
            f.write(struct.pack('>I', stream_len))
            f.write(bitstream_bytes)


def decompress_color(input_path, output_path):

    with open(input_path, 'rb') as f:
        overall_header = f.read(2 + 2 + 1)  
        if len(overall_header) < 5:
            raise ValueError("File header too short.")
        height, width, channels = struct.unpack('>HHB', overall_header)
        
        reconstructed_image = np.zeros((height, width, channels), dtype=np.uint8)
        
        for ch in range(channels):
            chan_header = f.read(16)
            if len(chan_header) < 16:
                raise ValueError("Channel header too short.")
            h_chan, w_chan, c0, cn, n = struct.unpack('>HHIII', chan_header)
            
            if h_chan != height or w_chan != width:
                raise ValueError("Mismatch in channel dimensions.")

            stream_len_bytes = f.read(4)
            (stream_len,) = struct.unpack('>I', stream_len_bytes)
            bitstream_bytes = f.read(stream_len)
            bitstream = bitarray()
            bitstream.frombytes(bitstream_bytes)
            
            C = np.zeros(n, dtype=np.int32)
            C[0] = c0
            C[-1] = cn
            deic(bitstream, C, 0, n - 1)
            
            N = np.zeros(n, dtype=np.int32)
            N[0] = C[0]
            for i in range(1, n):
                N[i] = C[i] - C[i - 1]
            
            errors = np.zeros(n, dtype=np.int32)
            for i in range(n):
                if N[i] % 2 == 0:
                    errors[i] = N[i] // 2
                else:
                    errors[i] = -(N[i] + 1) // 2

            recon_channel = predict_inverse(errors, width, height)
            reconstructed_image[:, :, ch] = np.clip(recon_channel, 0, 255).astype(np.uint8)

        decompressed_image = Image.fromarray(reconstructed_image)
        decompressed_image.save(output_path)


if __name__ == '__main__':
    image = Image.open('img.jpg').convert('RGB')
    image_data = np.array(image, dtype=np.int32)
    
    height, width, channels = image_data.shape
    print(f"Image dimensions: {width}x{height}, Channels: {channels}")

    start_time = time.time()
    compress_color(image_data, 'compressed.dat')
    print(f"Compression Time: {time.time() - start_time:.2f} seconds")

    original_size = os.path.getsize('img.jpg')
    compressed_size = os.path.getsize('compressed.dat')
    print(f"Original Size: {original_size} bytes")
    print(f"Compressed Size: {compressed_size} bytes")
    print(f"Compression Ratio: {original_size / compressed_size:.2f}")

    start_time = time.time()
    decompress_color('compressed.dat', 'decompressed.jpg')
    print(f"Decompression Time: {time.time() - start_time:.2f} seconds")
