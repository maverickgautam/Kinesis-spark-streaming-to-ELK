package com.amazonaws.services.kinesis.producer.sample;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import scala.util.Random;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.producer.KinesisProducer;
import com.amazonaws.services.kinesis.producer.KinesisProducerConfiguration;

public class simpleKinesisProducer {
    
    private static final String TIMESTAMP = Long.toString(System.currentTimeMillis());
    private static final int DATA_SIZE = 128;
    private static final int SECONDS_TO_RUN = 100;
    public static  String STREAM_NAME = "One";
    
    //public static final String STREAM_NAME = "driver";

    public static final String REGION = "us-west-2";

    
    public static KinesisProducer getKinesisProducer() {
        // There are many configurable parameters in the KPL. See the javadocs
        // on each each set method for details.
        KinesisProducerConfiguration config = new KinesisProducerConfiguration();
        
        // You can also load config from file. A sample properties file is
        // included in the project folder.
        // KinesisProducerConfiguration config =
        //     KinesisProducerConfiguration.fromPropertiesFile("default_config.properties");
        
        // If you're running in EC2 and want to use the same Kinesis region as
        // the one your instance is in, you can simply leave out the region
        // configuration; the KPL will retrieve it from EC2 metadata.
        config.setRegion(REGION);
        
        // You can pass credentials programmatically through the configuration,
        // similar to the AWS SDK. DefaultAWSCredentialsProviderChain is used
        // by default, so this configuration can be omitted if that is all
        // that is needed.
        //config.setCredentialsProvider(new DefaultAWSCredentialsProviderChain());
        
        // The maxConnections parameter can be used to control the degree of
        // parallelism when making HTTP requests. We're going to use only 1 here
        // since our throughput is fairly low. Using a high number will cause a
        // bunch of broken pipe errors to show up in the logs. This is due to
        // idle connections being closed by the server. Setting this value too
        // large may also cause request timeouts if you do not have enough
        // bandwidth.
        config.setMaxConnections(1);
        
        // Set a more generous timeout in case we're on a slow connection.
        config.setRequestTimeout(60000);
        
        // RecordMaxBufferedTime controls how long records are allowed to wait
        // in the KPL's buffers before being sent. Larger values increase
        // aggregation and reduces the number of Kinesis records put, which can
        // be helpful if you're getting throttled because of the records per
        // second limit on a shard. The default value is set very low to
        // minimize propagation delay, so we'll increase it here to get more
        // aggregation.
        config.setRecordMaxBufferedTime(1500);

        // If you have built the native binary yourself, you can point the Java
        // wrapper to it with the NativeExecutable option. If you want to pass
        // environment variables to the executable, you can either use a wrapper
        // shell script, or set them for the Java process, which will then pass
        // them on to the child process.
        // config.setNativeExecutable("my_directory/kinesis_producer");
        
        // If you end up using the default configuration (a Configuration instance
        // without any calls to set*), you can just leave the config argument
        // out.
        //
        // Note that if you do pass a Configuration instance, mutating that
        // instance after initializing KinesisProducer has no effect. We do not
        // support dynamic re-configuration at the moment.
        KinesisProducer producer = new KinesisProducer(config);
        
        return producer;
    }

    
    public static void main(String[] args) throws InterruptedException{
        STREAM_NAME=args[0];
        
        
        final KinesisProducer producer = getKinesisProducer();           
        for (int i = 100 ; i < 200; i++) {
            String json2 = "\n{\"id\": \""+i+"\",\"epochseconds\":123456792,\"state\":1,\"geoAttributes\": {\"location\": \"12.9715987,77.5945627\"}}\n";
            ByteBuffer data = ByteBuffer.wrap(json2.getBytes());
            producer.addUserRecord  (STREAM_NAME, TIMESTAMP, Utils.randomExplicitHashKey(), data); 
            producer.addUserRecord  (STREAM_NAME, TIMESTAMP, data); 
            
            Thread.sleep(100);
        }
        System.out.println("Put Result complete");        
    } 
}
