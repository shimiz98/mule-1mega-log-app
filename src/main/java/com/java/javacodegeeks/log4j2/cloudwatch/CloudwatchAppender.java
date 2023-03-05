package com.java.javacodegeeks.log4j2.cloudwatch;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.CreateLogStreamResult;
import com.amazonaws.services.logs.model.DataAlreadyAcceptedException;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.InvalidSequenceTokenException;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;
import com.amazonaws.client.builder.AwsClientBuilder;
 
@Plugin(name = "CLOUDW", category = "Core", elementType = "appender", printObject = true)
public class CloudwatchAppender extends AbstractAppender {
	  
	 /**
	  * 
	  */
	 private static final long serialVersionUID = 12321345L;
	  
	 private static Logger logger2 = LogManager.getLogger(CloudwatchAppender.class);
	 
	 private final Boolean DEBUG_MODE = System.getProperty("log4j.debug") != null;
	 
	 // sh98 begin
	 private final Path MY_FILE_PATH = new File("/tmp/my-file-log.log").toPath();
	 // sh98 end
	    /**
	     * Used to make sure that on close() our daemon thread isn't also trying to sendMessage()s
	     */
	    private Object sendMessagesLock = new Object();
	 
	    /**
	     * The queue used to buffer log entries
	     */
	    private LinkedBlockingQueue<LogEvent> loggingEventsQueue;
	 
	    /**
	     * the AWS Cloudwatch Logs API client
	     */
	    private AWSLogs awsLogsClient;
	 
	    private AtomicReference lastSequenceToken = new AtomicReference<>();
	 
	    /**
	     * The AWS Cloudwatch Log group name
	     */
	    private String logGroupName;
	 
	    /**
	     * The AWS Cloudwatch Log stream name
	     */
	    private String logStreamName;
	 
	    /**
	     * The queue / buffer size
	     */
	    private int queueLength = 1024;
	    
	    private String awsAccessKey;
	    private String awsAccessSecret;
	    private String awsRegion;
	    private String endpoint;
	 
	    /**
	     * The maximum number of log entries to send in one go to the AWS Cloudwatch Log service
	     */
	    private int messagesBatchSize = 128;
	
		private long sleepTime = 20L;
	 
	    private AtomicBoolean cloudwatchAppenderInitialised = new AtomicBoolean(false);
	  
	 
	    private CloudwatchAppender(final String name,
	                           final Layout layout,
	                           final Filter filter,
	                           final boolean ignoreExceptions,String logGroupName, 
	                           String logStreamName,
	                           final String awsAccessKey,
	                           final String awsSecretKey,
	                           final String awsRegion,
	                           Integer queueLength,
	                           Integer messagesBatchSize,
	                           String endpoint,
	                           Long sleepTime
	                           ) {
	        super(name, filter, layout, ignoreExceptions);
	        this.logGroupName = logGroupName;
	        this.logStreamName = logStreamName;
	        this.awsAccessKey = awsAccessKey;
	        this.awsAccessSecret = awsSecretKey;
	        this.awsRegion = awsRegion;
	        this.queueLength = queueLength;
	        this.messagesBatchSize = messagesBatchSize;
	        this.endpoint = endpoint;
	        this.sleepTime = sleepTime;
	        this.activateOptions();
	    }
	 
	    @Override
	    public void append(LogEvent event) {
	        if (cloudwatchAppenderInitialised.get()) {
	          boolean isAdded = loggingEventsQueue.offer(event.toImmutable());
	          if (!isAdded) {
	            // sh98 begin
	            try {
	              String myMsg = String.format("%s\tloggingEventsQueue is FULL\tloggingEventsQueue\t%d\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), loggingEventsQueue.size());
	              Files.write(MY_FILE_PATH, myMsg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	            } catch (IOException e) {
	              // give up. do nothing
	            }
	            // sh98 end
	          }
	         } else {
	             // just do nothing
	         }
	    }
	     
	    public void activateOptions() {
	        if (isBlank(logGroupName) || isBlank(logStreamName)) {
	            logger2.error("Could not initialise CloudwatchAppender because either or both LogGroupName(" + logGroupName + ") and LogStreamName(" + logStreamName + ") are null or empty");
	            this.stop();
	        } else {
	            //Credentials management could be customized
	            com.amazonaws.services.logs.AWSLogsClientBuilder clientBuilder = com.amazonaws.services.logs.AWSLogsClientBuilder.standard();
	            clientBuilder.setCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(this.awsAccessKey, this.awsAccessSecret)));
	            if (this.endpoint != null) {
	                 clientBuilder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(this.endpoint, this.awsRegion));
	            } else {
	                 clientBuilder.withRegion(Regions.fromName(awsRegion));
	            }
	            this.awsLogsClient = clientBuilder.build();
	            loggingEventsQueue = new LinkedBlockingQueue<>(queueLength);
	            try {
	                initializeCloudwatchResources();
	                initCloudwatchDaemon();
	                cloudwatchAppenderInitialised.set(true);
	            } catch (Exception e) {
	                logger2.error("Could not initialise Cloudwatch Logs for LogGroupName: " + logGroupName + " and LogStreamName: " + logStreamName, e);
	                if (DEBUG_MODE) {
	                    System.err.println("Could not initialise Cloudwatch Logs for LogGroupName: " + logGroupName + " and LogStreamName: " + logStreamName);
	                    e.printStackTrace();
	                }
	            }
	        }
	    }
	     
	    private void initCloudwatchDaemon() {
	     Thread t = new Thread(() -> {
	            while (true) {
	                try {
	                    if (loggingEventsQueue.size() > 0) {
	                        sendMessages();
	                    }
	                    Thread.currentThread().sleep(sleepTime);
	                } catch (InterruptedException e) {
	                    if (DEBUG_MODE) {
	                        e.printStackTrace();
	                    }
	                }
	            }
	        });
	     t.setName("CloudwatchThread");
	     t.setDaemon(true);
	     t.start();
	    }
	     
	    private void sendMessages() {
	        synchronized (sendMessagesLock) {
	            LogEvent polledLoggingEvent;
	            final Layout layout = getLayout();
	            List<LogEvent> loggingEvents = new ArrayList<>();
	 
	            try {
	                while (loggingEvents.size() < messagesBatchSize && (polledLoggingEvent = (LogEvent) loggingEventsQueue.poll()) != null) {
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                    loggingEvents.add(polledLoggingEvent);
	                }
	               
	                List inputLogEvents = loggingEvents.stream()
	                        .map(loggingEvent -> new InputLogEvent().withTimestamp(loggingEvent.getTimeMillis())
	                          .withMessage
	                          (
	                            layout == null ?
	                            loggingEvent.getMessage().getFormattedMessage():
	                            new String(layout.toByteArray(loggingEvent), StandardCharsets.UTF_8)
	                            )
	                          )
	                        .sorted(comparing(InputLogEvent::getTimestamp))
	                        .collect(toList());
	 
	                if (!inputLogEvents.isEmpty()) {
	
	                
	                    PutLogEventsRequest putLogEventsRequest = new PutLogEventsRequest(
	                            logGroupName,
	                            logStreamName,
	                            inputLogEvents);
	 
	                    try {
		                    // sh98 begin
		                    String myMsg = String.format("%s inputLogEvents.size()=%d\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), inputLogEvents.size());
		                    Files.write(MY_FILE_PATH, myMsg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		                    // sh98 end
	                        putLogEventsRequest.setSequenceToken((String)lastSequenceToken.get());
	                        PutLogEventsResult result = awsLogsClient.putLogEvents(putLogEventsRequest);
	                        lastSequenceToken.set(result.getNextSequenceToken());
		                    // sh98 begin
		                    String myMsg2 = String.format("%s result.getRejectedLogEventsInfo()=%s %s\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), result.getRejectedLogEventsInfo(), result.getSdkHttpMetadata().getAllHttpHeaders());
		                    Files.write(MY_FILE_PATH, myMsg2.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		                    // sh98 end
	                    } catch (DataAlreadyAcceptedException dataAlreadyAcceptedExcepted) {
	                      
	                        putLogEventsRequest.setSequenceToken(dataAlreadyAcceptedExcepted.getExpectedSequenceToken());
	                        PutLogEventsResult result = awsLogsClient.putLogEvents(putLogEventsRequest);
	                        lastSequenceToken.set(result.getNextSequenceToken());
	                        if (DEBUG_MODE) {
	                            dataAlreadyAcceptedExcepted.printStackTrace();
	                        }
	                        // sh98 begin
	                        String myMsg = String.format("%s Exception: %s\n%s\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), dataAlreadyAcceptedExcepted.toString());
	                     	Files.write(MY_FILE_PATH, myMsg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	                     	// sh98 end
	                    } catch (InvalidSequenceTokenException invalidSequenceTokenException) {
	                        putLogEventsRequest.setSequenceToken(invalidSequenceTokenException.getExpectedSequenceToken());
	                        PutLogEventsResult result = awsLogsClient.putLogEvents(putLogEventsRequest);
	                        lastSequenceToken.set(result.getNextSequenceToken());
	                        if (DEBUG_MODE) {
	                            invalidSequenceTokenException.printStackTrace();
	                        }
	                        // sh98 begin
	                        String myMsg = String.format("%s Exception: %s\n%s\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), invalidSequenceTokenException.toString());
	                     	Files.write(MY_FILE_PATH, myMsg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	                     	// sh98 end
	                    }
	                }
	            } catch (Exception e) {
	                if (DEBUG_MODE) {
	                 logger2.error(" error inserting cloudwatch:",e);
	                    e.printStackTrace();
	                }
                    // sh98 begin
                    try {
                        String myMsg = String.format("%s Exception: %s\n%s\n", DateTimeFormatter.ISO_INSTANT.format(Instant.now()), e.toString(), Arrays.toString(e.getStackTrace()));
                    	Files.write(MY_FILE_PATH, myMsg.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException ioe) {
                    	// give up. do nothing.
                    }
                    // sh98 end
	            }
	        }
	    }
	 
	    private void initializeCloudwatchResources() {
	 
	        DescribeLogGroupsRequest describeLogGroupsRequest = new DescribeLogGroupsRequest();
	        describeLogGroupsRequest.setLogGroupNamePrefix(logGroupName);
	 
	        Optional logGroupOptional = awsLogsClient
	                .describeLogGroups(describeLogGroupsRequest)
	                .getLogGroups()
	                .stream()
	                .filter(logGroup -> logGroup.getLogGroupName().equals(logGroupName))
	                .findFirst();
	 
	        if (!logGroupOptional.isPresent()) {
	            CreateLogGroupRequest createLogGroupRequest = new CreateLogGroupRequest().withLogGroupName(logGroupName);
	            awsLogsClient.createLogGroup(createLogGroupRequest);
	        }
	 
	        DescribeLogStreamsRequest describeLogStreamsRequest = new DescribeLogStreamsRequest().withLogGroupName(logGroupName).withLogStreamNamePrefix(logStreamName);
	 
	        Optional logStreamOptional = awsLogsClient
	                .describeLogStreams(describeLogStreamsRequest)
	                .getLogStreams()
	                .stream()
	                .filter(logStream -> logStream.getLogStreamName().equals(logStreamName))
	                .findFirst();
	        if (!logStreamOptional.isPresent()) {
	            CreateLogStreamRequest createLogStreamRequest = new CreateLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName);
	            CreateLogStreamResult o = awsLogsClient.createLogStream(createLogStreamRequest);
	        }
	 
	    }
	     
	    private boolean isBlank(String string) {
	        return null == string || string.trim().length() == 0;
	    }
	    protected String getSimpleStacktraceAsString(final Throwable thrown) {
	        final StringBuilder stackTraceBuilder = new StringBuilder();
	        for (StackTraceElement stackTraceElement : thrown.getStackTrace()) {
	            new Formatter(stackTraceBuilder).format("%s.%s(%s:%d)%n",
	                    stackTraceElement.getClassName(),
	                    stackTraceElement.getMethodName(),
	                    stackTraceElement.getFileName(),
	                    stackTraceElement.getLineNumber());
	        }
	        return stackTraceBuilder.toString();
	    }
	 
	    @Override
	    public void start() {
	        super.start();
	    }
	 
	    @Override
	    public void stop() {
	        super.stop();
	        while (loggingEventsQueue != null && !loggingEventsQueue.isEmpty()) {
	            this.sendMessages();
	        }
	    }
	 
	    @Override
	    public String toString() {
	        return CloudwatchAppender.class.getSimpleName() + "{"
	                + "name=" + getName() + " loggroupName=" + logGroupName
	                +" logstreamName=" + logStreamName;
	                
	    }
	 
	    @PluginFactory
	    @SuppressWarnings("unused")
	    public static CloudwatchAppender createCloudWatchAppender(
	      @PluginAttribute(value = "queueLength" ) Integer queueLength,
	                                                  @PluginElement("Layout") Layout layout,
	                                                  @PluginAttribute(value = "logGroupName") String logGroupName,
	                                                  @PluginAttribute(value = "logStreamName") String logStreamName,
	                                                  @PluginAttribute(value = "awsAccessKey") String awsAccessKey,
	                                                  @PluginAttribute(value = "awsSecretKey") String awsSecretKey,
	                                                  @PluginAttribute(value = "awsRegion") String awsRegion,
	                                                  @PluginAttribute(value = "name") String name,
	                                                  @PluginAttribute(value = "ignoreExceptions", defaultBoolean = false) Boolean ignoreExceptions,
	                                                   
	                                                  @PluginAttribute(value = "messagesBatchSize") Integer messagesBatchSize,
	                                                  @PluginAttribute(value = "endpoint") String endpoint,
	                                                  @PluginAttribute(value = "sleepTime", defaultLong = 20L) Long sleepTime
	                                                  )
	    {
	     return new CloudwatchAppender(name, layout, null, ignoreExceptions, logGroupName, logStreamName , awsAccessKey, awsSecretKey, awsRegion, queueLength,messagesBatchSize,endpoint, sleepTime);
	    }
	}
