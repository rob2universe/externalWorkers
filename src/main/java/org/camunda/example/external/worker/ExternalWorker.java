package org.camunda.example.external.worker;


import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.client.ExternalTaskClient;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.bpm.client.topic.TopicSubscriptionBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalTime;
import java.util.Random;

@Slf4j
public class ExternalWorker implements ExternalTaskHandler {

    public static final String REST_URL = "http://localhost:8080/rest";
    public static final String START_URL = REST_URL + "/process-definition/key/ParallelABC/start";
    public static final int PROCESSINSTANCES = 20;
    public static final int WORKERS = 2;
    public static final int MAX_TASK = 10;

    private ExternalTaskClient externalTaskClient;

    public ExternalWorker(String workerId, String topic) {
        externalTaskClient = ExternalTaskClient.create()
                .baseUrl(REST_URL)
                .asyncResponseTimeout(10000)
                .workerId(workerId)
                .maxTasks(MAX_TASK)
                .build();

        TopicSubscriptionBuilder builder = externalTaskClient
                .subscribe(topic)
                .lockDuration(1000)
                .handler(this);
        builder.open();
    }

    public static void main(String[] args) {

        for (int i = 1; i < PROCESSINSTANCES + 1; i++) startProcessInstance(i);

        for (int w = 1; w < WORKERS + 1; w++) {
            new ExternalWorker("Worker-A" + w, "TopicA");
            new ExternalWorker("Worker-B" + w, "TopicB");
            new ExternalWorker("Worker-C" + w, "TopicC");
        }
    }

    public static void startProcessInstance(long l) {
        try {
            URL url = new URL(START_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            OutputStream os = conn.getOutputStream();
            os.write(new String("{\"businessKey\" : " + l + "}").getBytes());
            os.flush();
            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            String output;
            while ((output = br.readLine()) != null) log.info(output);
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void execute(ExternalTask externalTask, ExternalTaskService externalTaskService) {
        log.info("Worker with ID " + externalTask.getWorkerId() + " working from " + LocalTime.now()
                + "\n on processDefinitionId=" + externalTask.getProcessDefinitionId()
                + " with businesskey= " + externalTask.getBusinessKey()
                + ", activityId=" + externalTask.getActivityId());

        for(int i = 0; i < 500; i++) {
            log.info(externalTask.getWorkerId() + " " + externalTask.getActivityId() + " " + i);
        }

//        try {
//            Thread.sleep(1000 ); //+ new Random().nextInt(2000)
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        externalTaskService.complete(externalTask);
        log.info("Worker with ID " + externalTask.getWorkerId() + " completed at " + LocalTime.now());
    }
}