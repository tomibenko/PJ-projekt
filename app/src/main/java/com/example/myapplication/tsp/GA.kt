package com.example.myapplication.tsp

class GA (
    var populationSize: Int,
    var crossoverChance: Double,
    var mutationChance: Double
) {
    var population: MutableList<Tour> = mutableListOf()
    var offspring: MutableList<Tour> = mutableListOf()
    var genCount = 0
    fun run (problem: TSP): Tour {
        var best = Tour(problem.number)

        for(i in 0..<populationSize) {
            val newTour = problem.generateTour()
            problem.evaluate(newTour)
            population.add(newTour)
            if (newTour.distance < best.distance) {
                best = newTour.copy()
            }
        }

        while(problem.currentEval < problem.maxFe) {
            offspring.add(getBest().copy())

            while(offspring.size < populationSize){
                val parent1 = tournamentSelection()
                val parent2 = tournamentSelection()

                if(RandomUtils.nextDouble() < crossoverChance) {
                    val children = partialMapCrossover(parent1, parent2)
                    offspring.add(children[0].copy())
                    if (offspring.size < populationSize) offspring.add(children[1].copy())
                }
                else {
                    offspring.add(parent1.copy())
                    if (offspring.size < populationSize) offspring.add(parent2.copy())
                }
            }

            for(member in offspring) {
                var mutateMember = member
                if(RandomUtils.nextDouble() < mutationChance) {
                    mutateMember = swapMutation(mutateMember).copy()
                }

                problem.evaluate(mutateMember)
                if(mutateMember.distance < best.distance) {
                    best = mutateMember.copy()
                }
            }

            population = offspring.toMutableList()
            offspring.clear()
            genCount++
        }

        return best
    }

    private fun getBest(): Tour {
        var best: Tour = population[0].copy()
        for(tour in population) {
            if(tour.distance < best.distance) {
                best = tour.copy()
            }
        }

        return best
    }

    private fun tournamentSelection(): Tour {
        val index1 = RandomUtils.nextInt(population.size)
        var index2 = RandomUtils.nextInt(population.size)

        if(index1 == index2) {
            if(index2 == population.size - 1) {
                index2 = 0
            }
            else {
                index2 += 1
            }
        }

        if(population[index1].distance < population[index2].distance) {
            return population[index1].copy()
        }
        else {
            return population[index2].copy()
        }
    }

    private fun partialMapCrossover(parent1: Tour, parent2: Tour): List<Tour> {
        val size = parent1.path.size
        val index1 = RandomUtils.nextInt(size / 2)
        val index2 = RandomUtils.nextInt(size / 2 + 1, size)

        val child1 = Tour(size)
        val child2 = Tour(size)

        for(i in index1 until index2) {
            child2.path[i] = parent1.path[i].copy()
            child1.path[i] = parent2.path[i].copy()
        }

        val mapping1to2 = (index1 until index2).associate { parent1.path[it] to parent2.path[it] }
        val mapping2to1 = (index1 until index2).associate { parent2.path[it] to parent1.path[it] }

        for(i in 0 until size) {
            if(i < index1 || i >= index2) {
                var currentCity = parent1.path[i].copy()

                while(currentCity in mapping2to1) {
                    currentCity = mapping2to1[currentCity]!!.copy()
                }

                if(currentCity !in child2.path) {
                    child2.path[i] = currentCity.copy()
                }
            }
        }

        for(i in 0 until size) {
            if(i < index1 || i >= index2) {
                var currentCity = parent2.path[i].copy()

                while(currentCity in mapping1to2) {
                    currentCity = mapping1to2[currentCity]!!.copy()
                }

                if(currentCity !in child1.path) {
                    child1.path[i] = currentCity.copy()
                }
            }
        }

        return listOf(child2, child1)
    }

    private fun swapMutation (member: Tour): Tour {
        val index1 = RandomUtils.nextInt(member.dimension)
        var index2 = RandomUtils.nextInt(member.dimension)

        if(index1 == index2) {
            if(index2 == member.dimension - 1) {
                index2 = 0
            }
            else{
                index2 += 1
            }
        }

        val city1 = member.path[index1].copy()
        val city2 = member.path[index2].copy()

        member.setCity(index1, city2)
        member.setCity(index2, city1)
        return member
    }
}