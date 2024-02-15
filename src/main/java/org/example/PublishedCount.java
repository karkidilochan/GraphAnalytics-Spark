
package org.example;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public final class PublishedCount {

        public static void main(String[] args) throws Exception {

                // Configure Spark
                final SparkConf sparkConf = new SparkConf()
                                .setAppName("PublishedCount");
                // .setMaster("local"); // Set the master to local for running it locally

                // Create Spark context
                final JavaSparkContext ctx = new JavaSparkContext(sparkConf);

                // Load dataset published-dates-redo.txt
                final JavaRDD<String> publishedData = ctx.textFile(args[0], 1);

                final JavaRDD<String> citationsData = ctx.textFile(args[1], 1);

                JavaPairRDD<String, Integer> yearCountPairs = publishedData.mapToPair(line -> {
                        String[] parts = line.split("\t");
                        String year = parts[1].split("-")[0];
                        return new Tuple2<>(year, 1);
                });

                /*
                 * START OF TASK 1:
                 */

                // Reduce by key to count the number of papers published each year
                JavaPairRDD<String, Integer> publicationsPerYear = yearCountPairs.reduceByKey(Integer::sum);

                List<Tuple2<String, Integer>> nodes_list = publicationsPerYear.collect();

                // Write publications data to a CSV file
                try (FileWriter writer = new FileWriter("publications_per_year.csv")) {
                        writer.write("Year,Publications\n");
                        for (Tuple2<String, Integer> tuple : nodes_list) {
                                writer.write(tuple._1() + "," + tuple._2() + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }

                System.out.println("Number of papers published per year:");
                // Collect RDD and print its contents
                for (Tuple2<String, Integer> tuple : publicationsPerYear.collect()) {
                        System.out.println(tuple._1() + ": " + tuple._2());
                }

                // now we find the count of citations per year
                // Convert citations data to pairs of (id, cited id)
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

                // Write citations data to a CSV file
                try (FileWriter writer = new FileWriter("citations_per_year.csv")) {
                        writer.write("Year,Citations\n");
                        for (Tuple2<String, Integer> tuple : edges_list) {
                                writer.write(tuple._1() + "," + tuple._2() + "\n");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }

                /*
                 * START OF TASK 2:
                 */

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

                /* START OF TASK 3: (INCOMPLETE) */

                // Filter papers published in 1995
                // Filter published data for the year 1995
                JavaPairRDD<String, String> published1995 = published.filter(pair -> {
                        String date = pair._2();
                        return date.startsWith("1995");
                });

                // Join citations and filtered published data
                JavaPairRDD<String, Tuple2<String, String>> joinedData1995 = citations.join(published1995);

                // Extract required fields (paper ID and cited ID)
                JavaPairRDD<String, String> result1995 = joinedData1995.mapToPair(pair -> {
                        String paperId = pair._1();
                        String citedId = pair._2()._1();
                        return new Tuple2<>(paperId, citedId);
                });

                JavaPairRDD<String, Iterable<String>> graphData = result1995.groupByKey();

                // Display the resulting graph
                // graphData.collect().forEach(System.out::println);

                // compute distance from citing paper ID to all other nodes
                JavaPairRDD<String, Integer> distancesRDD = graphData.flatMapToPair(pair -> {
                        String citingPaper = pair._1;
                        Queue<Tuple2<String, Integer>> queue = new LinkedList<>();
                        Map<String, Integer> distances = new HashMap<>();
                        queue.add(new Tuple2<>(citingPaper, 0));
                        distances.put(citingPaper, 0);
                        List<Tuple2<String, Integer>> result = new ArrayList<>();

                        while (!queue.isEmpty()) {
                                Tuple2<String, Integer> current = queue.poll();
                                int currentDistance = current._2;

                                result.add(current);

                                for (String neighbor : pair._2) {
                                        if (!distances.containsKey(neighbor)) {
                                                distances.put(neighbor, currentDistance + 1);
                                                queue.add(new Tuple2<>(neighbor, currentDistance + 1));
                                        }
                                }
                        }
                        return result.iterator();
                });

                // Find diameter
                int diameter = distancesRDD.mapToPair(pair -> new Tuple2<>(pair._2(), 1))
                                .reduceByKey(Integer::sum)
                                .sortByKey(false)
                                .first()._1();

                System.out.println("The diameter of the citation graph at 1995 is: " + diameter);

                // Stop the Spark context
                ctx.stop();
        }
}
