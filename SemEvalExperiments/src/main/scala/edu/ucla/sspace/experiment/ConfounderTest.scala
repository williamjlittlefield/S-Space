package edu.ucla.sspace.experiment

import edu.ucla.sspace.similarity.CosineSimilarity
import edu.ucla.sspace.vector.CompactSparseVector
import edu.ucla.sspace.vector.SparseDoubleVector
import edu.ucla.sspace.vector.VectorIO

import scala.collection.JavaConversions.collectionAsScalaIterable
import scala.io.Source
import scala.util.Random

import java.io.FileReader


object ConfounderTest {
    def main(args: Array[String]) {
        // Load the test key file.  This will let us map from known labels to instance identifiers for each word.
        val testLabels = Source.fromFile(args(0)).getLines
                               .map(_.split("\\s+"))
                               .toList
                               .groupBy(_(2))
        // Read the context vectors for the test set.
        val contexts = VectorIO.readSparseVectors(new FileReader(args(1)))
        // Read the headers that map to the matching index in the context list.
        val headers = Source.fromFile(args(2)).getLines.zipWithIndex.toList.toMap
        // Load in the word senses we've learned.
        val clusters = VectorIO.readSparseVectors(new FileReader(args(3)))
        // Create the similarity function, this will be used to compare test instances to senses..
        val simFun = new CosineSimilarity()

        var numPairs = 0
        var numValid = 0

        var numBelligs = 0
        var numGood = 0

        // Turn the test label mapping into a mapping from test labels to lists of context vectors and the best similarity to a sense for
        // each context.
        val testContexts = testLabels.map{ case (k, ids) =>
            (k, ids.map(_(1))
                   .map(headers)
                   .map(contexts.get)
                   .map( v => (v, clusters.map(simFun.sim(_, v)).max )))
        }

        for ( List(group1, group2) <- testContexts.toList.combinations(2) ) {
            for ( (i1Context, bestI1Sim) <- group1._2;
                  (i2Context, bestI2Sim) <- group2._2) {
                // Create a zellig that will consist of features from the known context and the confounder context.  We can use this zellig
                // to evaluate both true contexts from each group at the same time since it's a mixture of each one.
                val zellig = mixContexts(i1Context, i2Context)

                // Compute the best similarity to a sense for the zellig context.
                val bestZgSim = clusters.map(simFun.sim(_, zellig)).max

                // If the best similiarity for an instance is larger than it's zellig, count this as a correct test.  Do this for both test
                // instances available.
                if (bestI1Sim > bestZgSim)
                    numValid += 1
                numPairs += 1

                if (bestI2Sim > bestZgSim)
                    numValid += 1
                numPairs += 1

                for ( (altContext, _) <- group1._2; if altContext != i1Context) {
                    val bellig = mixContexts(i1Context, altContext)
                    val bestBgSim = clusters.map(simFun.sim(_, bellig)).max
                    if (bestBgSim > bestZgSim) 
                        numGood += 1
                    numBelligs += 1

                }
            }
        }
        printf("%s zellig %f\n", args(4), numValid/numPairs.toDouble)
        printf("%s bellig %f\n", args(4), numGood/numBelligs.toDouble)
    }

    def mixContexts(initial: SparseDoubleVector, mixin: SparseDoubleVector) = {
        // Create a zellig that will consist of features from the known context and the confounder context.  We can use this zellig
        // to evaluate both true contexts from each group at the same time since it's a mixture of each one.
        val zellig = new CompactSparseVector(initial)
        // Randomly set half of the contexts in the original context to zero.
        val nonZeros = zellig.getNonZeroIndices
        val half = nonZeros.size/2
        for ( id <- Random.shuffle(nonZeros.toList).take(half))
            zellig.set(id, 0d)

        // Randomly add in the same number of features from the confounder context.
        for ( id <- Random.shuffle(mixin.getNonZeroIndices.toList).take(half))
            zellig.add(id, mixin.get(id))
        zellig
    }
}