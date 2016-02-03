package com.amazonaws.services.kinesis.producer.sample;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.*;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kinesis.KinesisUtils;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;

import scala.Tuple2;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;

/**
 * Consumes messages from a Amazon Kinesis streams and does wordcount.
 *
 * This example spins up 1 Kinesis Receiver per shard for the given stream. It then starts pulling from the last
 * checkpointed sequence number of the given stream.
 *
 * Usage: JavaKinesisWordCountASL [app-name] [stream-name] [endpoint-url] [region-name] [app-name] is the name of the
 * consumer app, used to track the read data in DynamoDB [stream-name] name of the Kinesis stream (ie. mySparkStream)
 * [endpoint-url] endpoint of the Kinesis service (e.g. https://kinesis.us-east-1.amazonaws.com)
 *
 *
 * Example: # export AWS keys if necessary $ export AWS_ACCESS_KEY_ID=[your-access-key] $ export
 * AWS_SECRET_KEY=<your-secret-key>
 *
 * # run the example $ SPARK_HOME/bin/run-example streaming.JavaKinesisWordCountASL myAppName mySparkStream \
 * https://kinesis.us-east-1.amazonaws.com
 *
 * There is a companion helper class called KinesisWordProducerASL which puts dummy data onto the Kinesis stream.
 *
 * This code uses the DefaultAWSCredentialsProviderChain to find credentials in the following order: Environment
 * Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY Java System Properties - aws.accessKeyId and aws.secretKey
 * Credential profiles file - default location (~/.aws/credentials) shared by all AWS SDKs Instance profile credentials
 * - delivered through the Amazon EC2 metadata service For more information, see
 * http://docs.aws.amazon.com/AWSSdkDocsJava/latest/DeveloperGuide/credentials.html
 *
 * See http://spark.apache.org/docs/latest/streaming-kinesis-integration.html for more details on the Kinesis Spark
 * Streaming integration.
 */
public final class SparkToElasticSearchUsingELKClient { // needs to be public for access from run-example
    private static final Logger logger = Logger.getLogger(SparkToElasticSearchUsingELKClient.class);

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        // Check that all required args were passed in.
        if (args.length < 2) {
            System.err.println("Usage: JavaKinesisWordCountASL <stream-name> <endpoint-url>\n\n"
                    + "    <app-name> is the name of the app, used to track the read data in DynamoDB\n"
                    + "    <stream-name> is the name of the Kinesis stream\n"
                    + "    <endpoint-url> is the endpoint of the Kinesis service\n"
                    + "                   (e.g. https://kinesis.us-east-1.amazonaws.com)\n"
                    + "Generate data for the Kinesis stream using the example KinesisWordProducerASL.\n"
                    + "See http://spark.apache.org/docs/latest/streaming-kinesis-integration.html for more\n"
                    + "details.\n");
            for (int i = 0; i < args.length; i++) {
                System.out.println(args[i]);
            }
            System.out.println("length " + args.length);
            System.exit(1);
        }
        
        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);


        // Set default log4j logging level to WARN to hide Spark logs

        // Populate the appropriate variables from the given args
        String kinesisAppName = "KinesisReader"+UUID.randomUUID();
        String streamName = args[0];
        String endpointUrl = args[1];

        // Create a Kinesis client in order to determine the number of shards for the given stream
        AmazonKinesisClient kinesisClient = new AmazonKinesisClient(new DefaultAWSCredentialsProviderChain());
        kinesisClient.setEndpoint(endpointUrl);
        int numShards = kinesisClient.describeStream(streamName).getStreamDescription().getShards().size();



        // In this example, we're going to create 1 Kinesis Receiver/input DStream for each shard.
        // This is not a necessity; if there are less receivers/DStreams than the number of shards,
        // then the shards will be automatically distributed among the receivers and each receiver
        // will receive data from multiple shards.
        int numStreams = numShards;

        // Spark Streaming batch interval
        Duration batchInterval = new Duration(1000);

        // Kinesis checkpoint interval. Same as batchInterval for this example.
        Duration kinesisCheckpointInterval = batchInterval;

        // Get the region name from the endpoint URL to save Kinesis Client Library metadata in
        // DynamoDB of the same region as the Kinesis stream
        String regionName = RegionUtils.getRegionByEndpoint(endpointUrl).getName();

        // Setup the Spark config and StreamingContext
        SparkConf sparkConfig = new SparkConf().setAppName("SparkStreaming");
        sparkConfig.set("es.index.auto.create", "true");
        //sparkConfig.set("es.port", "9200");
        sparkConfig.set("es.nodes","172.31.35.70:9200");
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConfig, batchInterval);

        // Create the Kinesis DStreams
        List<JavaDStream<byte[]>> streamsList = new ArrayList<JavaDStream<byte[]>>(numStreams);
        for (int i = 0; i < numStreams; i++) {
            streamsList.add(KinesisUtils.createStream(jssc, kinesisAppName, streamName, endpointUrl, regionName,
                    InitialPositionInStream.LATEST, kinesisCheckpointInterval, StorageLevel.MEMORY_AND_DISK_2()));
        }

        // Union all the streams if there is more than 1 stream
        JavaDStream<byte[]> unionStreams;
        if (streamsList.size() > 1) {
            unionStreams = jssc.union(streamsList.get(0), streamsList.subList(1, streamsList.size()));
        } else {
            // Otherwise, just use the 1 stream
            unionStreams = streamsList.get(0);
        }

        JavaDStream<String> words1 =  unionStreams.map(new Function<byte[], String>(){
            @Override
            public String call(byte[] v1) throws Exception {
                // TODO Auto-generated method stub
                String s = new String(v1, StandardCharsets.UTF_8);
                logger.info("String json is " + s );
                return s;
            }});
        
        words1.foreachRDD(new Function<JavaRDD<String>, Void>() {
            @Override
            public Void call(JavaRDD<String> rdd) throws Exception {
                logger.info("i am before null " );
                if(null!=rdd){
                    logger.info("i am after null " );
                JavaEsSpark.saveJsonToEs(rdd, "inventory1/supply");
                }
                return null;
            }
        });
               
        jssc.start();
        jssc.awaitTermination();
    }
}
