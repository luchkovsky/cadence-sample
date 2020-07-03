package ru.stas.cadence.samples.LocalActivityWorkflow;

public class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public String composeGreeting(String greeting) {
      /*  System.out.println("Activity composeGreeting. Started");
        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpGet get = new HttpGet("http://127.0.0.1:8099/longoperation");
        try {
          System.out.println("Activity composeGreeting. Send long request");*/
        try {
            System.out.println("Activity composeGreeting. Sending long request");
            Thread.sleep(15000);
            System.out.println("Activity composeGreeting. Sent request");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //CloseableHttpResponse response = httpclient.execute(get);
        // System.out.println("Activity composeGreeting. Status " + response.getStatusLine());
        //System.out.println("Activity composeGreeting. Get response");
    /*    } catch (IOException e) {
          System.out.println("Activity composeGreeting. Error");
          throw new RuntimeException(e);
        }
      System.out.println("Activity composeGreeting. Completed");Completed */
        return "Greeting " + greeting + "!";
    }

}
