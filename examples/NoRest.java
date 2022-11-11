/* Copyright 2022 Brian Ziman www.brianziman.com */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Simple web server that responds to GET/POST requests by waiting a short time
 * and then returning an ID.
 *
 * GET /generateId?id=some_id HTTP/1.0
 *
 * HTTP/1.1 200 OK
 * Content-Type: text/plain
 * Content-Length: 20
 *
 * { "jobId" : "UUID" }
 *
 */
public class NoRest implements Runnable {
  private static final Set<String> SUPPORTED_METHODS = Set.of("GET");
  private static final String EXPECTED_URI = "/generateId";

  private final ExecutorService threadPool;
  private final int port;
  private volatile boolean running = true;

  private final ConcurrentHashMap<String, UUID> jobs = new ConcurrentHashMap<>();

  NoRest(int port) {
    this.port = port;
    this.threadPool = Executors.newCachedThreadPool();
  }

  public void startup() {
    threadPool.execute(this);
  }

  public void run() {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      serverSocket.setSoTimeout(1000); // 1 second
      while (running) {
        try {
          threadPool.execute(new Worker(serverSocket.accept()));
        } catch (SocketTimeoutException ignore) {
          // This can be ignored, all other exceptions bail out.
          continue;
        } catch (Exception e) {
          e.printStackTrace();
          break;
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      threadPool.shutdown();
    }
    System.out.println("Bye bye.");
  }

  void shutdown() {
    running = false;
  }

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(args[0]);

    NoRest noRest = new NoRest(port);

    Thread runner = new Thread(noRest);
    runner.start();
    Scanner scanner = new Scanner(System.in);
    System.out.println("Press enter to quit.");
    try {
      scanner.nextLine();
    } catch (NoSuchElementException ohwell) {
      // We typed Ctrl-D instead of Enter.
    } finally {
      noRest.shutdown();
      runner.join();
    }
  }

  class Worker implements Runnable {
    private final Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    Worker(Socket socket) {
      this.socket = socket;
    }

    public void run() {
      try {
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()), 65536);
      } catch (IOException e) {
        e.printStackTrace();
      }
      try {
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
      } catch (IOException e) {
        e.printStackTrace();
      }
      if (reader != null && writer != null) {
        try {
          processRequest(reader, writer);
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
      try {
        socket.close();
      } catch (IOException ignore) {}
    }

    void processRequest(BufferedReader reader, PrintWriter writer) throws IOException {
      // NOTE: This is NOT safe for public internet usage!
      String line = reader.readLine();
      String[] parts = line.split("\\s+|\\?|=");
      if (parts.length != 5) {
        writeError(400, "Invalid request\r\n");
      }
      String method = parts[0];
      String uri = parts[1];
      String queryKey = parts[2];
      String queryValue = parts[3];
      String version = parts[4];
      if (!version.startsWith("HTTP/")) {
        writeError(400, "Unsupported version: " + version + "\r\n");
        return;
      }
      // Now eat headers until we reach a blank line of input...
      for (;;) {
        line = reader.readLine();
        if (line.isEmpty()) break;
      }
      if (!SUPPORTED_METHODS.contains(method)) {
        writeError(400, "Unsupported method: " + method + "\r\n");
        return;
      }
      if (!uri.startsWith(EXPECTED_URI)) {
        writeError(404, "Unsupported URI: " + uri + "\r\n");
        return;
      }
      if (!queryKey.equals("id")) {
        writeError(400, "Invalid query: " + queryKey + "\r\n");
        return;
      }
      
      UUID uuid = jobs.computeIfAbsent(queryValue, unused -> UUID.randomUUID());

      String response = String.format("{ \"jobId\" : \"%s\" }\r\n", uuid.toString());
      writeOkay(response);
    }

    void writeOkay(String response) throws IOException {
      // output specifically requires CR NL, by HTTP standard.
      writer.printf("HTTP/1.1 200 OK\r\n");
      writer.printf("Content-Type: application/json\r\n");
      writer.printf("Content-Length: %d\r\n", response.length());
      writer.printf("Connection: close\r\n");
      writer.printf("\r\n");
      try {
        // Make it slow, randomly between 1 and 10 seconds.
        Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 10000));
      } catch (Exception ignore) {}
      writer.printf(response);
      writer.flush();
      System.out.print("Generated: " + response);
    }

    void writeError(int code, String error) throws IOException {
      // output specifically requires CR NL, by HTTP standard.
      writer.printf("HTTP/1.1 %d %s\r\n", code, getMessage(code));
      writer.printf("Content-Type: text/plain\r\n");
      writer.printf("Content-Length: %d\r\n", error.length());
      writer.printf("Connection: close\r\n");
      writer.printf("\r\n");
      writer.printf(error);
      writer.flush();
      System.out.print("Error: " + error);
    }

    String getMessage(int code) {
      switch (code) {
        case 404:
          return "Not Found";
        default:
          return "Bad Request";
      }
    }


  }

}
