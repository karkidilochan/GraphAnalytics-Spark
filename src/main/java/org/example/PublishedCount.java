
package org.example;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public final class PublishedCount {
         private static final Pattern SPACE = Pattern.compile(" ");

        public static void main(String[] args) throws Exception {
                if (args.length < 2) {
                        System.err.println("Usage: GraphCount <published_data> <citations_data>");
                        System.exit(1);
                }
                // Configure Spark
                final SparkConf sparkConf = new SparkConf()
                                .setAppName("PublishedCount")
                                .setMaster("local"); // Set the master to local for running it locally
                // Create Spark context
                final JavaSparkContext ctx = new JavaSparkContext(sparkConf);
                // Load dataset published-dates-redo.txt
                final JavaRDD<String> publishedData = ctx.textFile("src/main/java/published-dates-redo.txt", 1);

                final JavaRDD<String> citationsData = ctx.textFile("src/main/java/citations-redo.txt",1);

                System.out.println(publishedData);
                System.out.println(citationsData);
//
//                // Convert each line to a Tuple2 of (year, 1)
                JavaPairRDD<String, Integer> yearCountPairs = publishedData.mapToPair(line -> {
                        String[] parts = line.split("\t");
                        String year = parts[1].split("-")[0];
                        return new Tuple2<>(year, 1);
                });

                System.out.println(yearCountPairs);

                // Reduce by key to count the number of papers published each year
                JavaPairRDD<String, Integer> publicationsPerYear = yearCountPairs.reduceByKey(Integer::sum);
                System.out.println("Number of papers published per year:");
//                Collect RDD and print its contents
                for (Tuple2<String, Integer> tuple : publicationsPerYear.collect()) {
                        System.out.println(tuple._1() + ": " + tuple._2());
                }

                /*now we find the count of citations per year */
//              Convert citations data to pairs of (id, cited id)
                JavaPairRDD<String, String> citations = citationsData.mapToPair(line -> {
                        String[] parts = line.split("\t");
                        return new Tuple2<>(parts[0], parts[1]);
                });

                // Convert published data to pairs (paper ID, publication date)
                JavaPairRDD<String, String> published = publishedData.mapToPair(line -> {
                        String[] parts = line.split("\t");
                        return new Tuple2<>(parts[0], parts[1]);
                });

                // Join citations with publication dates based on paper ID
                JavaPairRDD<String, Tuple2<String, String>> joined = citations.join(published);

                // Extract year from publication date and count citations per year
                JavaPairRDD<String, Integer> citationsPerYear = joined.mapToPair(tuple -> {
                        String year = tuple._2()._2().split("-")[0];
                        return new Tuple2<>(year, 1);
                }).reduceByKey(Integer::sum);

                // Collect the RDD and print its contents
                System.out.println("Number of citations generated per year:");
                for (Tuple2<String, Integer> pair : citationsPerYear.collect()) {
                        System.out.println(pair._1() + ": " + pair._2());
                }

                List<Tuple2<String, Integer>> edges_list = citationsPerYear.collect();
                List<Tuple2<String, Integer>> nodes_list = publicationsPerYear.collect();
//                citations_list.sort(Comparator.comparingInt(tuple -> Integer.parseInt(tuple._1())));
                // Reduce by key to compute cumulative count of publications per year
                JavaPairRDD<String, Integer> cumulativePublicationsPerYear = publicationsPerYear
                        .sortByKey() // Sort by year to ensure cumulative count is computed correctly
                        .mapToPair(pair -> {
                                String year = pair._1();
                                int count = pair._2();
                                return new Tuple2<>(year, count);
                        })
                        .mapPartitionsToPair(iter -> {
                                int cumulativeCount = 0;
                                List<Tuple2<String, Integer>> result = new ArrayList<>();
                                while (iter.hasNext()) {
                                        Tuple2<String, Integer> current = iter.next();
                                        cumulativeCount += current._2();
                                        result.add(new Tuple2<>(current._1(), cumulativeCount));
                                }
                                return result.iterator();
                        });

// Reduce by key to compute cumulative count of citations per year
                JavaPairRDD<String, Integer> cumulativeCitationsPerYear = citationsPerYear
                        .sortByKey() // Sort by year to ensure cumulative count is computed correctly
                        .mapToPair(pair -> {
                                String year = pair._1();
                                int count = pair._2();
                                return new Tuple2<>(year, count);
                        })
                        .mapPartitionsToPair(iter -> {
                                int cumulativeCount = 0;
                                List<Tuple2<String, Integer>> result = new ArrayList<>();
                                while (iter.hasNext()) {
                                        Tuple2<String, Integer> current = iter.next();
                                        cumulativeCount += current._2();
                                        result.add(new Tuple2<>(current._1(), cumulativeCount));
                                }
                                return result.iterator();
                        });

// Print cumulative count of publications per year
                System.out.println("Cumulative number of papers published per year:");
                for (Tuple2<String, Integer> tuple : cumulativePublicationsPerYear.collect()) {
                        System.out.println(tuple._1() + ": " + tuple._2());
                }

// Print cumulative count of citations per year
                System.out.println("Cumulative number of citations per year:");
                for (Tuple2<String, Integer> tuple : cumulativeCitationsPerYear.collect()) {
                        System.out.println(tuple._1() + ": " + tuple._2());
                }

                List<Tuple2<String, Integer>> cumulative_edges_list = cumulativeCitationsPerYear.collect();
                List<Tuple2<String, Integer>> cumulative_nodes_list = cumulativePublicationsPerYear.collect();



// Write citations data to a CSV file
                try (FileWriter writer = new FileWriter("citations_per_year.csv")) {
                        writer.write("Year,Citations\n");
                        for (Tuple2<String, Integer> tuple : edges_list) {
                                writer.write(tuple._1() + "," + tuple._2() + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }
// Write publications data to a CSV file
                try (FileWriter writer = new FileWriter("publications_per_year.csv")) {
                        writer.write("Year,Publications\n");
                        for (Tuple2<String, Integer> tuple : nodes_list) {
                                writer.write(tuple._1() + "," + tuple._2() + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }


                // Write data to a CSV file
                try (FileWriter writer = new FileWriter("edges_vs_nodes.csv")) {
                        writer.write("Year,Publications,Citations\n");
                        for (Tuple2<String, Integer> tuple : cumulative_nodes_list) {
                                String year = tuple._1();
                                Integer publications = tuple._2();
                                // Find corresponding citations for the year
                                Integer edge = 0;
                                for (Tuple2<String, Integer> citationsTuple : cumulative_edges_list) {
                                        if (citationsTuple._1().equals(year)) {
                                                edge = citationsTuple._2();
                                                break;
                                        }
                                }
                                writer.write(year + "," + publications + "," + edge + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }


//                Start of Task 2
//                // Extract year from publication date
//                JavaPairRDD<String, String> publicationYear = publishedData.mapToPair(line -> {
//                        String[] parts = line.split("\t");
//                        String year = parts[1].split("-")[0];
//                        return new Tuple2<>(parts[0], year);
//                });
//
//                // Convert citations data to pairs (source, destination)
//                JavaPairRDD<String, String> edges = citationsData.mapToPair(line -> {
//                        String[] parts = line.split("\t");
//                        return new Tuple2<>(parts[0], parts[1]);
//                });
//
//                // Join edges with publication year based on source paper ID
//                JavaPairRDD<String, Tuple2<String, String>> joinedEdges = edges.join(publicationYear);
//
//                // Count the number of edges and nodes per year
//                JavaPairRDD<Tuple2<String, String>, Long> edgesCount = joinedEdges.mapToPair(tuple -> {
//                        String year = tuple._2()._2();
//                        return new Tuple2<>(new Tuple2<>(year, tuple._1()), 1L);
//                }).distinct().groupByKey().mapValues(Iterable::spliterator).mapValues(spliterator -> spliterator.estimateSize());
//
//                JavaPairRDD<String, Long> nodesCount = publicationYear.mapToPair(pair -> new Tuple2<>(pair._2(), 1L)).distinct().groupByKey().mapValues(Iterable::spliterator).mapValues(spliterator -> spliterator.estimateSize());

//                // Collect the results
//                for (Tuple2<Tuple2<String, String>, Long> edgeCount : edgesCount.collect()) {
//                        System.out.println("Year: " + edgeCount._1()._1() + ", Edges: " + edgeCount._2());
//                }
//
//                for (Tuple2<String, Long> nodeCount : nodesCount.collect()) {
//                        System.out.println("Year: " + nodeCount._1() + ", Nodes: " + nodeCount._2());
//                }





//                 Save the result to an output file
//                 yearCounts.saveAsTextFile("/published_per_year_count.txt");

                /*
                 * Start of Task 1: count the number of unique papers published per year
                 * For that, take the published-dates-redo.txt, and increase the counter for a
                 * particular year until december of that year
                 */

//                 // Split each line into words
//                 final JavaRDD<String> words = lines.flatMap(s ->
//                 Arrays.asList(SPACE.split(s)).iterator());
//                 // Map each word to a pair (word, 1)
//                 final JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new
//                 Tuple2<>(s, 1));
//                 // Count the occurrences of each word
//                 final JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1 + i2);
//                 // Collect and print the results
//                 final List<Tuple2<String, Integer>> output = counts.collect();
//                 for (Tuple2<?, ?> tuple : output) {
//                 System.out.println(tuple._1() + ": " + tuple._2());
//                 }


                // Collecting data from RDDs
//                List<Tuple2<String, Integer>> publicationsData = publicationsPerYear.collect();
//                List<Tuple2<String, Integer>> citationsDataList = citationsPerYear.collect();

                // Stop the Spark context
                ctx.stop();
        }
}
