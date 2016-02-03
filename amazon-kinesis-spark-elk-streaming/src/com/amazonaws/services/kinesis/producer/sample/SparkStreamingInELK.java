package com.amazonaws.services.kinesis.producer.sample;



import java.util.ArrayList;
import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;

/**
 * 
 * This is a sample code to demonstrate how to stream data via spark into ELK
 * Assumption is ELK instance is either AWS managed or either manually setup EC2 instance
 * 
 * ElasticSearch streaming  plugin has been used to write data into ELK
 * Mapping has been setup before data can be inserted
 * 
 * @author kunalgautam
 *
 */


public final class SparkStreamingInELK { // needs to be public for access from run-example

    public static void main(String[] args) {
        // Setup the Spark config and StreamingContext
        SparkConf sparkConfig = new SparkConf().setAppName("ElasticSearch");
        sparkConfig.set("es.index.auto.create", "true");
        //sparkConfig.set("es.port", "9200");
        //In case of AWS ELK replace ip:90200 with the endpoint url . ELK endpoint works on port 80
        sparkConfig.set("es.nodes","172.31.35.70:9200");
        //sparkConfig.set("es.nodes","search-grabtaxi-elk-ndl2pvqgqmj3ixmwpsmyvnmray.us-west-2.es.amazonaws.com");
        //sparkConfig.set("es.nodes.discovery","false");
        JavaSparkContext sc = new JavaSparkContext(sparkConfig);       
        List<String> list = new ArrayList<String>();
        
        for(int i =0;i<1000;i++){
        String json2 = "{\"id\": \""+i+"\",\"epochseconds\":123456792,\"state\":1,\"geoAttributes\": {\"location\": \"-73.945648193359375,40.807811737060547\"}}";
        list.add(json2);
        }
        JavaRDD<String> javaRDD = sc.parallelize(list);
        JavaEsSpark.saveJsonToEs(javaRDD, "inventory1/supply");
        sc.close();

    }
}
