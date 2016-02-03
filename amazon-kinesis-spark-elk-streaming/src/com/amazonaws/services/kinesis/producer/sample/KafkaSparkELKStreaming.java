package com.amazonaws.services.kinesis.producer.sample;



/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */


import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;

import scala.Tuple2;

/**
 * Consumes messages from one or more topics in Kafka and writes to ELK.
 * 
 * 
 * Usage: JavaKafkaWordCount <zkQuorum> <group> <topics> <numThreads> <zkQuorum> is a list of one or more zookeeper
 * servers that make quorum <group> is the name of kafka consumer group <topics> is a list of one or more kafka topics
 * to consume from <numThreads> is the number of threads the kafka consumer should use
 *
 * To run this example: `$ bin/run-example org.apache.spark.examples.streaming.KafkaStreaming zoo01,zoo02, \ zoo03
 *my-consumer-group topic1,topic2 1` 
 *
 *
 *spark-submit --jars elasticsearch-spark_2.10-2.1.2.jar,spark-streaming-kafka_2.10-1.6
 * .0.jar,kafka_2.10-0.8.2.1.jar,kafka-clients-0.8.2.1.jar,zkclient-0.3.jar,metrics-core-2.2.0.jar,footing-tuple-0.2.jar
 * --class com.amazonaws.services.kinesis.producer.sample.KafkaStreaming --master local[4] --verbose
 * amazon-kinesis-producer-sample-1.0.0.jar 172.31.45.112:2181 co-group MyTopic 1
 */

public final class KafkaSparkELKStreaming {
    private static final Logger logger = Logger.getLogger(KafkaSparkELKStreaming.class);

    private KafkaSparkELKStreaming() {}

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: JavaKafkaWordCount <zkQuorum> <group> <topics> <numThreads>");
            System.exit(1);
        }

        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);

        SparkConf sparkConf = new SparkConf().setAppName("JavaKafkaELKStreaming");
        sparkConf.set("es.index.auto.create", "true");
        // sparkConfig.set("es.port", "9200");
        sparkConf.set("es.nodes", "172.31.35.70:9200");
        // Create the context with 2 seconds batch size
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, new Duration(2000));

        int numThreads = Integer.parseInt(args[3]);
        Map<String, Integer> topicMap = new HashMap<String, Integer>();
        String[] topics = args[2].split(",");
        for (String topic : topics) {
            topicMap.put(topic, numThreads);
        }

        JavaPairReceiverInputDStream<String, String> messages =
                KafkaUtils.createStream(jssc, args[0], args[1], topicMap);

        JavaDStream<String> data = messages.map(new Function<Tuple2<String, String>, String>() {
            public String call(Tuple2<String, String> message) {
                // System.out.println("NewMessage: " + message._2() + "++++++++++++++++++");
                return message._2();
            }
        });

        data.foreachRDD(new Function<JavaRDD<String>, Void>() {
            @Override
            public Void call(JavaRDD<String> rdd) throws Exception {
                if (null != rdd) {
                    JavaEsSpark.saveJsonToEs(rdd, "inventory1/supply");
                }
                return null;
            }
        });

        // data.print();

        jssc.start();
        jssc.awaitTermination();
    }
}
