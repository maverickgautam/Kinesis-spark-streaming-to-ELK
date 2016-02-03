package com.amazonaws.services.kinesis.producer.sample;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Index;

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
import org.apache.spark.api.java.function.Function;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kinesis.KinesisUtils;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;

/**
 * Consumes messages from a Amazon Kinesis streams and streams to ELK to give realTimeView of Supply and Demand.
 * 
 * This code uses the DefaultAWSCredentialsProviderChain to find credentials in the following order: Environment
 * 
 * Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_KEY Java System Properties - aws.accessKeyId and aws.secretKey
 * 
 * Credential profiles file - default location (~/.aws/credentials) shared by all AWS SDKs Instance profile credentials
 * 
 */

public final class KenesisSparkElasticSearchStreamingAWS { // needs to be public for access from run-example
    private static final Pattern WORD_SEPARATOR = Pattern.compile("\n");
    private static final Logger logger = Logger.getLogger(KenesisSparkElasticSearchStreamingAWS.class);
    private static String Elkendpoint;
    private static int enableDebugging = 0;

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        // Check that all required args were passed in.
        if (args.length < 4) {
            System.err
                    .println("Usage: KenesisSparkElasticAWS <stream-name> <kenesis-endpoint-url>  <Elk-endPoint> <0/1 for enablingDebugging>\n\n");
            System.exit(1);
        }

        // Logging Turned off for one to see output on screen
        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);

        // Set default log4j logging level to WARN to hide Spark logs

        // Populate the appropriate variables from the given args
        // A random name is choosen to avoid dynamo DB checkpoint conflicts
        String kinesisAppName = "KinesisReader" + UUID.randomUUID();
        // Kinesis Stream name
        String streamName = args[0];
        // Kinesis Endpoint
        String endpointUrl = args[1];
        // ELK endpoint (REST over HTTP and not TCP)
        Elkendpoint = args[2];
        enableDebugging = Integer.parseInt(args[3]);

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
        SparkConf sparkConfig = new SparkConf().setAppName("KenesisToElkSparkStreaming");
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

        // returned value is a stream , which will containt junk values along with original data
        JavaDStream<String> jsonInput = unionStreams.map(new Function<byte[], String>() {
            @Override
            public String call(byte[] v1) throws Exception {
                return new String(v1, StandardCharsets.UTF_8);
            }
        });

        jsonInput.foreachRDD(new Function<JavaRDD<String>, Void>() {
            @Override
            public Void call(JavaRDD<String> rdd) throws Exception {
                if (null != rdd) {
                    List<String> incoming = rdd.toArray();
                    // This is REST over HTTP and not TCP
                    String connectionUrl = Elkendpoint;
                    JestClientFactory factory = new JestClientFactory();
                    factory.setHttpClientConfig(new HttpClientConfig.Builder(connectionUrl).multiThreaded(false)
                            .build());
                    JestClient client = factory.getObject();

                    for (String in : incoming) {

                        for (String S : Arrays.asList(WORD_SEPARATOR.split(in))) {
                            if (S.startsWith("{")) {
                                Index index = new Index.Builder(S).index("inventory1").type("supply").build();
                                JestResult r = client.execute(index);
                                if (enableDebugging != 0) {
                                    System.out.println("Incoming Json is " + S);
                                    System.out.println("put into ELK isSucceeded " + r.isSucceeded()
                                            + " Error Message " + r.getErrorMessage());
                                }
                            }
                        }
                    }
                    // Dont Forget to shutdown client
                    client.shutdownClient();
                }
                return null;
            }
        });

        jssc.start();
        jssc.awaitTermination();
    }
}
