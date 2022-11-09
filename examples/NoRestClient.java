/* Copyright 2022 Brian Ziman www.brianziman.com */
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * A client that calls a slow RESTful service to generate IDs, aggregating the results
 * into a single JSON report.
 */
public class NoRestClient {
  private static final String URL = "http://localhost:8000/generateId";

  private final ExecutorService executorService = Executors.newCachedThreadPool();

  public CompletableFuture<String> fetchId() throws Exception {
    HttpClient client = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .executor(executorService)
      .build();

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(URL))
      .build();

    return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
      .thenApply(HttpResponse::body)
      .thenApply(s -> s.split("\"")[3]);
  }

  public void shutdown() {
    System.err.println("Shutting down...");
    executorService.shutdown();
    try {
      executorService.awaitTermination(30, TimeUnit.SECONDS);
    } catch (Exception ignored) {}
    System.err.println("Terminated...");
  }

  public static void main(String[] args) throws Exception {
    int count = Integer.parseInt(args[0]);

    NoRestClient client = new NoRestClient();
    try {

      List<CompletableFuture<String>> futures = new ArrayList<>();
      long start = System.currentTimeMillis();
      System.err.print("Making requests");
      for (int i = 0; i < count; i++) {
        System.err.print(".");
        futures.add(client.fetchId());
      }
      System.out.println(" and waiting.");
      String json =
          toFutureOfList(futures)
             .thenApply(
                 list ->
                    list.stream()
                        .map(s -> String.format("\"%s\"", s))
                        .collect(Collectors.joining(", ", "[ ", " ]")))
             .get();
      System.out.println(json);
      long end = System.currentTimeMillis();
      System.err.println("Elapsed time: " + ((end - start)/1000) + " seconds.");
    } finally {
      client.shutdown();
    }
  }

  static <T> CompletableFuture<List<T>> toFutureOfList(List<CompletableFuture<T>> futures) {
    return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
      .thenApply(
          unused ->
              futures.stream().map(CompletableFuture::join).collect(Collectors.toList()));
  }

}
