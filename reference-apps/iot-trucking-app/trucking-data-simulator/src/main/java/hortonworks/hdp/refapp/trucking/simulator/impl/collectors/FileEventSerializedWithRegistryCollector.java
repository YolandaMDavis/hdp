package hortonworks.hdp.refapp.trucking.simulator.impl.collectors;

import hortonworks.hdp.refapp.trucking.simulator.impl.domain.transport.EventSourceType;
import hortonworks.hdp.refapp.trucking.simulator.impl.domain.transport.MobileEyeEvent;
import hortonworks.hdp.refapp.trucking.simulator.schemaregistry.TruckSchemaConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.hortonworks.registries.schemaregistry.SchemaCompatibility;
import com.hortonworks.registries.schemaregistry.SchemaMetadata;
import com.hortonworks.registries.schemaregistry.avro.AvroSchemaProvider;
import com.hortonworks.registries.schemaregistry.client.SchemaRegistryClient;
import com.hortonworks.registries.schemaregistry.serdes.avro.AvroSnapshotSerializer;


public class FileEventSerializedWithRegistryCollector extends BaseTruckEventCollector {


	private static final String LINE_BREAK = "\n";
	private byte[] LINE_BREAK_BYTES = LINE_BREAK.getBytes();
	

	private File truckEventsFile;
	private String schemaRegistryUrl;
	private EventSourceType eventSourceType;

	public FileEventSerializedWithRegistryCollector(String fileName, EventSourceType eventSource,  String schemaRegistryUrl) {
       this.truckEventsFile = new File(fileName);
       this.schemaRegistryUrl = schemaRegistryUrl;
       this.eventSourceType = eventSource;
       logger.info("Using Schema Registry["+schemaRegistryUrl+"] to serialize events");
      
	}
	
	@Override
	public void onReceive(Object event) throws Exception {
		
		
		MobileEyeEvent mee = (MobileEyeEvent) event;
		
		if(eventSourceType == null || EventSourceType.ALL_STREAMS.equals(eventSourceType)) {
			sendTruckEventToFile(mee);	
			sendTruckSpeedEventToFile(mee);			
		} else if(EventSourceType.GEO_EVENT_STREAM.equals(eventSourceType)) {
			sendTruckEventToFile(mee);
		} else if (EventSourceType.SPEED_STREAM.equals(eventSourceType)) {
			sendTruckSpeedEventToFile(mee);	
		}

	}

	private void sendTruckEventToFile(MobileEyeEvent mee) throws Exception {
		
		byte[] serializedPayload = serializeTruckGeoEvent(mee);
		
		logger.debug("Creating serialized truck geo event["+serializedPayload+"] for driver["+mee.getTruck().getDriver().getDriverId() + "] in truck [" + mee.getTruck() + "]");			
		
		try {
			FileUtils.writeByteArrayToFile(truckEventsFile, serializedPayload, true);
			//FileUtils.writeByteArrayToFile(truckEventsFile, LINE_BREAK_BYTES, true);
			
			
		} catch (Exception e) {
			logger.error("Error sending serialized event[" + serializedPayload + "] to file[ " + truckEventsFile + " ] ", e);
		}		
		
	}

	private void sendTruckSpeedEventToFile(MobileEyeEvent mee) throws Exception {

		byte[] serializedPayload = serializeTruckSpeedEvent(mee);
		logger.debug("Creating serialized truck speed event["+serializedPayload+"] for driver["+mee.getTruck().getDriver().getDriverId() + "] in truck [" + mee.getTruck() + "]");			
		
		try {
			FileUtils.writeByteArrayToFile(truckEventsFile, serializedPayload, true);
			//FileUtils.writeByteArrayToFile(truckEventsFile, LINE_BREAK_BYTES, true);
		} catch (Exception e) {
			logger.error("Error sending serialized event[" + serializedPayload + "] to file[ " + truckEventsFile + " ] ", e);
		}	
	}
	
	public byte[] serializeTruckGeoEvent(MobileEyeEvent event) throws Exception  {
		
		
		
		//get serializer info from registry
		AvroSnapshotSerializer serializer = createSerializer();		
				
		Object truckGeoEvent = createGenericRecordForTruckGeoEvent("/schema/truck-geo-event-log.avsc", event);

	
       // Now we have the payload in right format (Avro GenericRecord), lets serialize
       SchemaMetadata schemaMetadata = new SchemaMetadata.Builder(TruckSchemaConfig.LOG_TRUCK_GEO_EVENT_SCHEMA_NAME)
		  .type(AvroSchemaProvider.TYPE)
		  .schemaGroup(TruckSchemaConfig.LOG_SCHEMA_GROUP_NAME)
		  .description("Truck Geo Events from trucks")
		  .compatibility(SchemaCompatibility.BACKWARD)
		  .build();       
		byte[] serializedPaylod = serializer.serialize(truckGeoEvent, schemaMetadata);

		return serializedPaylod;
		
	}
	
	public byte[] serializeTruckSpeedEvent(MobileEyeEvent event) throws Exception  {
		
		
		
		//get serializer info from registry
		AvroSnapshotSerializer serializer = createSerializer();		
				
		Object truckGeoEvent = createGenericRecordForTruckSpeedEvent("/schema/truck-speed-event-log.avsc", event);

	
       // Now we have the payload in right format (Avro GenericRecord), lets serialize
       SchemaMetadata schemaMetadata = new SchemaMetadata.Builder(TruckSchemaConfig.LOG_TRUCK_SPEED_EVENT_SCHEMA_NAME)
		  .type(AvroSchemaProvider.TYPE)
		  .schemaGroup(TruckSchemaConfig.LOG_SCHEMA_GROUP_NAME)
		  .description("Truck Speed Events from trucks")
		  .compatibility(SchemaCompatibility.BACKWARD)
		  .build();       
		byte[] serializedPaylod = serializer.serialize(truckGeoEvent, schemaMetadata);

		return serializedPaylod;
		
	}	

	private AvroSnapshotSerializer createSerializer() {
		AvroSnapshotSerializer serializer = new AvroSnapshotSerializer();		
		serializer.init(createConfig(schemaRegistryUrl));
		return serializer;
	}		
	
    private Map<String, Object> createConfig(String schemaRegistryUrl) {
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaRegistryClient.Configuration.SCHEMA_REGISTRY_URL.name(), schemaRegistryUrl);
        
        return config;
    }		
    
    protected Object createGenericRecordForTruckGeoEvent(String schemaFileName, MobileEyeEvent event) throws IOException {
    	 
    	Schema schema = new Schema.Parser().parse(getSchema(schemaFileName));
        
        GenericRecord avroRecord = new GenericData.Record(schema);
        String eventTime = new Timestamp(new Date().getTime()).toString();
        int truckId = event.getTruck().getTruckId();
        int driverId = event.getTruck().getDriver().getDriverId();
        int routeId = event.getTruck().getDriver().getRoute().getRouteId();
        double latitude = event.getLocation().getLatitude();
        double longitude = event.getLocation().getLongitude();
        long correlationId = event.getCorrelationId();
        
//        logger.info("TruckId: " + truckId);
//        logger.info("DriverId: " + driverId);
//        logger.info("routId: " + routeId);
//        logger.info("lat: " + latitude);
//        logger.info("longitude: " + longitude);
//        logger.info("correlationId: " + correlationId);
       
        
        avroRecord.put("eventTime", eventTime);
        avroRecord.put("eventSource", "truck_geo_event");
        avroRecord.put("truckId", truckId);
        avroRecord.put("driverId", driverId);
        avroRecord.put("driverName", event.getTruck().getDriver().getDriverName());
        avroRecord.put("routeId", routeId);
        avroRecord.put("route", event.getTruck().getDriver().getRoute().getRouteName());
        avroRecord.put("eventType", event.getEventType().toString());
        avroRecord.put("latitude", latitude);
        avroRecord.put("longitude", longitude);
        avroRecord.put("correlationId", correlationId);      
        
        logger.info(avroRecord.toString());
        
//        String eventTime = new Timestamp(new Date().getTime()).toString();
//        avroRecord.put("eventTime", eventTime);
//        avroRecord.put("eventSource", "truck_geo_event");
//        avroRecord.put("truckId", 40);
//        avroRecord.put("driverId", 23);
//        avroRecord.put("driverName", "Jeff Markham");
//        avroRecord.put("routeId", 1345);
//        avroRecord.put("route", "Saint Louis to Chicago");
//        avroRecord.put("eventType", "Lane Departure");
//        avroRecord.put("latitude", -82.52);
//        avroRecord.put("longitude", 40.7);
//        long correlationId = 100;
//        avroRecord.put("correlationId", correlationId);        

        return avroRecord;
    }	
    
    protected Object createGenericRecordForTruckSpeedEvent(String schemaFileName, MobileEyeEvent event) throws IOException {
   	 
    	Schema schema = new Schema.Parser().parse(getSchema(schemaFileName));
        
        GenericRecord avroRecord = new GenericData.Record(schema);
        avroRecord.put("eventTime", new Timestamp(new Date().getTime()).toString());
        avroRecord.put("eventSource", "truck_speed_event");
        avroRecord.put("truckId", event.getTruck().getTruckId());
        avroRecord.put("driverId", event.getTruck().getDriver().getDriverId());
        avroRecord.put("driverName", event.getTruck().getDriver().getDriverName());
        avroRecord.put("routeId", event.getTruck().getDriver().getRoute().getRouteId());
        avroRecord.put("route", event.getTruck().getDriver().getRoute().getRouteName());
        avroRecord.put("speed", event.getTruckSpeed());

        return avroRecord;
    }	    
    
    private String getSchema(String schemaFileName) throws IOException {
        InputStream schemaResourceStream = this.getClass().getResourceAsStream(schemaFileName);
        if (schemaResourceStream == null) {
            throw new IllegalArgumentException("Given schema file [" + schemaFileName + "] does not exist");
        }

        String schemaText = IOUtils.toString(schemaResourceStream, "UTF-8");
        return schemaText;
    }      
    

}
