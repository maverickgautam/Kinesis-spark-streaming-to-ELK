package com.amazonaws.services.kinesis.producer.sample;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.core.Index;

/**
 * AWS elastic search endpoint has a unique syntax. Its a REST endpoint and listens on port 80 The function provides you
 * with a working example of how to put data into elastic search
 * 
 * @author kunalgautam
 * 
 *To Create Mapping use :
 *curl -XPUT "ELKendpoint/_template/docvalues" -d '{"template": "inventory*","settings": {"refresh_interval": "30s","number_of_shards": 1},"mappings": {"supply": {"properties": {"id": {"type": "string","index": "not_analyzed","doc_values": true},"epochseconds": {"type": "date"},"state": {"type": "integer","index": "not_analyzed"},"geoAttributes": {"properties": {"location": {"type": "geo_point"}}}}}}}'
* To insert a doc use: curl -XPUT 'http://ElkEndpoint/inventory1/supply' -d '{"id": "kunal","epochseconds": "1454350821","state": 1,"geoAttributes": {"location": "41.12, -71.34"}}'
 *
 */


public class AWSElasticSearchConnector {

    public static void main(String[] args) throws Exception {
        // End point of AWS ELK.
        String connectionUrl = "http://search-grabtaxi-elk-ndl2pvqgqmj3ixmwpsmyvnmray.us-west-2.es.amazonaws.com";
        JestClientFactory factory = new JestClientFactory();
        factory.setHttpClientConfig(new HttpClientConfig.Builder(connectionUrl).multiThreaded(true).build());
        JestClient client = factory.getObject();
        //Json doc to be Inserted
        String json2 =
                "{\"id\":\"GAZAB1\",\"epochseconds\":123456792,\"state\":1,\"geoAttributes\": {\"location\": \"-73.945648193359375,40.807811737060547\"}}";

        // index Name: inventory1 , Type: supply
        Index index = new Index.Builder(json2).index("inventory1").type("supply").build();
        JestResult r = client.execute(index);
        System.out.println("Operation Succeded : " + r.isSucceeded() +  " errormessage is  " + r.getErrorMessage()  );
        client.shutdownClient();
        System.out.println("I am done ");
    }

}
