package com.example.myapplication.tsp

class Tour {
    var distance: Double
    var dimension: Int
    var path: MutableList<TSP.City>

    constructor(tour: Tour) {
        distance = tour.distance
        dimension = tour.dimension
        path = tour.path.toMutableList()
    }

    constructor(dimension: Int) {
        distance = Double.MAX_VALUE
        this.dimension = dimension
        path = MutableList(dimension) { TSP.City(0, 0.0, 0.0) }
    }

    fun copy(): Tour {
        return Tour(this)
    }

    fun setCity(index: Int, city: TSP.City) {
        path[index] = city
        distance = Double.MAX_VALUE
    }

    override fun toString(): String {
        var returnString: String = ""
        for(city in path)  {
            returnString += city.index
        }

        return returnString + "\n"
    }
}