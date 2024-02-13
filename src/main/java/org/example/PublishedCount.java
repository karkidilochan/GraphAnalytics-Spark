
package org.example;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;
// import java.util.Arrays;
// import java.util.List;
// import java.util.regex.Pattern;

public final class PublishedCount {
        // private static final Pattern SPACE = Pattern.compile(" ");

        public static void main(String[] args) throws Exception {
                if (args.length < 1) {
                        System.err.println("Usage: GraphCount <file>");
                        System.exit(1);
                }
                // Configure Spark
                final SparkConf sparkConf = new SparkConf()
                                .setAppName("WordCount")
                                .setMaster("local"); // Set the master to local for running it locally
                // Create Spark context
                final JavaSparkContext ctx = new JavaSparkContext(sparkConf);
                // Load dataset published-dates-redo.txt
                final JavaRDD<String> lines = ctx.textFile(args[0], 1);

                // Convert each line to a Tuple2 of (year, 1)
                JavaPairRDD<String, Integer> yearCountPairs = lines.mapToPair(line -> {
                        String[] parts = line.split("\t");
                        String year = parts[1].split("-")[0];
                        return new Tuple2<>(year, 1);
                });

                // Reduce by key to count the number of papers published each year
                JavaPairRDD<String, Integer> yearCounts = yearCountPairs.reduceByKey(Integer::sum);

                System.out.println(yearCounts);

                // Save the result to an output file
                // yearCounts.saveAsTextFile("output_path");

                /*
                 * Start of Task 1: count the number of unique papers published per year
                 * For that, take the published-dates-redo.txt, and increase the counter for a
                 * particular year until december of that year
                 */

                // // Split each line into words
                // final JavaRDD<String> words = lines.flatMap(s ->
                // Arrays.asList(SPACE.split(s)).iterator());
                // // Map each word to a pair (word, 1)
                // final JavaPairRDD<String, Integer> ones = words.mapToPair(s -> new
                // Tuple2<>(s, 1));
                // // Count the occurrences of each word
                // final JavaPairRDD<String, Integer> counts = ones.reduceByKey((i1, i2) -> i1
                // + i2);
                // // Collect and print the results
                // final List<Tuple2<String, Integer>> output = counts.collect();
                // for (Tuple2<?, ?> tuple : output) {
                // System.out.println(tuple._1() + ": " + tuple._2());
                // }
                // Stop the Spark context
                ctx.stop();
        }
}
