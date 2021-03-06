package com.amazonaws.services.kinesis.producer.sample;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.Iterator;

import scala.Tuple2;





import org.apache.spark.SparkConf;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.StorageLevels;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;


public final class SparkStreamingSample {
    private static final Pattern SPACE = Pattern.compile(" ");

    public static void main(String[] args) {
      if (args.length < 2) {
        System.err.println("Usage: JavaNetworkWordCount <hostname> <port>");
        System.exit(1);
      }


      // Create the context with a 1 second batch size
      SparkConf sparkConf = new SparkConf().setAppName("JavaNetworkWordCount");
      JavaStreamingContext ssc = new JavaStreamingContext(sparkConf, Durations.seconds(1));

      // Create a JavaReceiverInputDStream on target ip:port and count the
      // words in input stream of \n delimited text (eg. generated by 'nc')
      // Note that no duplication in storage level only for running locally.
      // Replication necessary in distributed scenario for fault tolerance.
      JavaReceiverInputDStream<String> lines = ssc.socketTextStream(
              args[0], Integer.parseInt(args[1]), StorageLevels.MEMORY_AND_DISK_SER);
      JavaDStream<String> words = lines.flatMap(new FlatMapFunction<String, String>() {
        @Override
        public Iterable<String> call(String x) {
          return (Iterable<String>) Arrays.asList(SPACE.split(x)).iterator();
        }
      });
      
      
      JavaPairDStream<String, Integer> wordCounts = words.mapToPair(
        new PairFunction<String, String, Integer>() {
          @Override
          public Tuple2<String, Integer> call(String s) {
            return new Tuple2<String, Integer>(s, 1);
          }
        }).reduceByKey(new Function2<Integer, Integer, Integer>() {
          @Override
          public Integer call(Integer i1, Integer i2) {
            return i1 + i2;
          }
        });

      wordCounts.print();
      ssc.start();
      ssc.awaitTermination();
    }
  }
