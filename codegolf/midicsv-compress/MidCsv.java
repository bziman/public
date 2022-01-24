// Copyright 2022 Brian Ziman - https://www.brianziman.com/
//
//   "This program is free software: you can redistribute it and/or modify it under the
//    terms of the GNU General Public License as published by the Free Software Foundation,
//    either version 3 of the License, or (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful, but WITHOUT ANY
//    WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
//    PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License along with this
//    program. If not, see <https://www.gnu.org/licenses/>."
//
// If you'd like to use this software in a way that is not compatible with GPLv3, please
// contact the author, and other licensing terms may be possible.

import java.util.*;
import java.util.stream.*;
import java.io.*;

/**
 * The challenge was to take a midicsv and to compress it as tightly as possible, with
 * the goal of having the smallest sum of the compressed size along with the size of the
 * decoder code.
 *
 * The input was Beethoven's Sonata No. 1 (1st Movement), the first midi link at:
 * https://www.mutopiaproject.org/cgibin/make-table.cgi?collection=beetson&amp;preview=1
 *
 * Then run that through midicsv (which I installed via apt-get), as documented at:
 * https://www.fourmilab.ch/webtools/midicsv/
 *
 * So far the best solution I can come up with is with a simple shell script, which converts
 * the csv back into a midi, and then bzip2's it.
 *
 * compress.sh:
 *
 *   #!/bin/bash
 *   CSVIN=$1
 *   COMPRESSED=$2
 *   csvmidi &lt; $CSVIN | bzip2 -c - &gt; $COMPRESSED
 *
 * decompress.sh:
 *   #!/bin/bash
 *   COMPRESSED=$1
 *   CSVOUT=$2
 *   bzip2 -d - &lt; $COMPRESSED | midicsv &gt; $CSVOUT
 *
 * The source csv file is 115,850 bytes, and this produces a compressed result of 3,128 bytes.
 *
 * But I thought that approach was cheating, so I wrote the approach implemented here, which
 * is much worse, compressing only to 6,751 bytes (though that's still an impressive 94%
 * compression ratio). Once you add in the size of this Java file, it doesn't fare as well,
 * though to be fair, one could strip out only the decoder, and minify it... or rewrite it
 * in a less verbose language. But where's the fun in that? Without comments, and good naming,
 * if I ever come back to this, I'll have no idea what I was thinking. Software engineering
 * before hacking; sorry, I'm old.
 *
 * The approach I used here was to recognize that there are very few distinct pieces of data
 * among all the rows - the most unique being the sequence number, which tends to have a
 * fairly regular delta from the previous record. So using deltas for the sequence number,
 * I used Huffman encoding to substantially reduce the required bits for each record.
 *
 * This is good, but not great, as the Burrows-Wheeler transform algorithm used by bzip2
 * can reduce entropy across records, which my approach does not.
 *
 * That said, my output cannot be compressed further (using bzip2), so I don't have any
 * obvious opportunities for further significant optimization.
 *
 * Note that simply apply bzip2 to the original CSV file only compresses to 11,269 bytes,
 * and you have to convert the CSV back to MIDI in order to achieve the 3,128 byte "best
 * solution I can come up with" result. So for a single step compression, my algorithm
 * is about twice as good as just BWT on the unprocessed input.
 *
 * I don't see any way to do better (or comparable) to the CSV-MIDI-bzip2 approach, and it
 * seems like re-implmenting those algorithms from scratch would, while fun, not be
 * particularly useful.
 */
public class MidCsv {

  // Utility for reading bits from a stream....
  static class BitReader {
    private final InputStream is;
    private final Queue<Boolean> queue = new ArrayDeque<>();
    BitReader(InputStream is) {
      this.is = is;
    }
    // read 1 bit, 0 or 1; -1 on EOF
    int read() throws IOException {
      if (queue.isEmpty()) {
        int next = is.read();
        if (next == -1) {
          return -1;
        }
        for (int i = 1 << 7; i != 0; i >>= 1) {
          queue.offer((next & i) != 0x0);
        }
      }
      return queue.poll() ? 1 : 0;
    }

    // Read the next "bits" bits from the stream, and convert it to an integer.
    int read(int bits) throws IOException {
      int ret = 0;
      for (int i = 0; i < bits; i++) {
        int ch = read();
        if (ch == -1) throw new RuntimeException("Ran out of bits");
        ret = (ret << 1) | ch;
      }
      return ret;
    }
  }

  // Utility for writing bits to a stream...
  static class BitStream implements Closeable, Flushable {
    private final OutputStream os;
    private final Deque<Boolean> queue = new ArrayDeque<>();
    int bitsWritten = 0;
    BitStream(OutputStream os) {
      this.os = os;
    }
    void write(String bits) throws IOException {
      bitsWritten += bits.length();
      if (os == null) {
        //System.err.println(bits);
      } else {
        for (int i = 0; i < bits.length(); i++) {
          queue.addLast(bits.charAt(i) == '1');
        }
        while (queue.size() >= 8) {
          int b = 0;
          for (int i = 0; i < 8; i++) {
            b = (b << 1) | (queue.poll() ? 1 : 0);
          }
          os.write(b);
        }
      }
    }
    /** Write the low bits of value to the stream */
    void write(int value, int bits) throws IOException {
      int max = 1 << bits;
      if (value >= max) {
        throw new RuntimeException(
            String.format("%d has more than %d bits", value, bits));
      }
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < bits; i++) {
        sb.append(value & 0x01);
        value >>= 1;
      }
      write(sb.reverse().toString());
    }
    public void close() throws IOException {
      if (os != null) {
        this.flush();
        os.close();
      }
    }
    public void flush() throws IOException {
      if (os != null) {
        while (!queue.isEmpty()) {
          write("0");
        }
        os.flush();
      }
    }
    int bitsWritten() {
      return bitsWritten;
    }
  }

  // A node in a Huffman tree.
  static class HNode implements Comparable<HNode> {
    static int nextValue = -1; // monotonically decreasing for interior nodes
    private final int value;
    private final int freq;
    private final HNode left;
    private final HNode right;
    HNode(int value, int freq, HNode left, HNode right) {
      this.value = value;
      this.freq = freq;
      this.left = left;
      this.right = right;
    }
    static HNode combine(HNode a, HNode b) { // a <= b
      return new HNode(nextValue--, a.freq + b.freq, a, b);
    }
    public int hashCode() {
      return 37 * value + freq;
    }
    public boolean equals(Object o) {
      return this.compareTo((HNode) o) == 0;
    }
    public int compareTo(HNode n) {
      int c = Integer.compare(freq, n.freq);
      if (c == 0) {
        c = Integer.compare(value, n.value);
      }
      return c;
    }
    public String toString() {
      return String.format("[%d, %d]", value, freq);
    }
  }

  static class Huffman {

    final int maximumNumberOfBits;
    final Map<Integer, String> encodings;
    final Map<String, Integer> decodings;

    Huffman(int maximumNumberOfBits, Map<String, Integer> decodings) {
      this.maximumNumberOfBits = maximumNumberOfBits;
      this.encodings = null;
      this.decodings = decodings;
    }

    Huffman(List<Integer> values) {
      decodings = null;
      Map<Integer, Integer> freqs = new HashMap<>();
      int max = 0;
      for (int n : values) {
        if (n < 0) continue;
        freqs.compute(n, (k, v) -> v == null ? 1 : v + 1);
        max = Math.max(n, max);
      }
      this.maximumNumberOfBits = 64 - Long.numberOfLeadingZeros(max);

      Queue<HNode> pqueue = new PriorityQueue<>();
      for (Map.Entry<Integer, Integer> entry : freqs.entrySet()) {
        HNode n = new HNode(entry.getKey(), entry.getValue(), null, null);
        pqueue.add(n);
      }

      while (pqueue.size() > 1) {
        HNode a = pqueue.poll();
        HNode b = pqueue.poll();
        pqueue.add(HNode.combine(a, b));
      }

      Deque<String> bitStack = new ArrayDeque<>();
      Deque<HNode> stack = new ArrayDeque<>();
      stack.add(pqueue.poll());
      this.encodings = new HashMap<>();
      while (!stack.isEmpty()) {
        HNode n = stack.pollLast();
        String bits = bitStack.isEmpty() ? "" : bitStack.pollLast();
        if (n.value >= 0) {
          encodings.put(n.value, bits);
        }
        if (n.left != null) {
          stack.add(n.left);
          bitStack.add(bits + "0");
        }
        if (n.right != null) {
          stack.add(n.right);
          bitStack.add(bits + "1");
        }
      }
    }

    void write(BitStream bs) throws IOException {
      int before = bs.bitsWritten();
      bs.write(maximumNumberOfBits, 5);
      bs.write(encodings.size(), 8);
      for (Map.Entry<Integer, String> entry : encodings.entrySet()) {
        System.err.println(entry);
        bs.write(entry.getKey(), maximumNumberOfBits);
        bs.write(entry.getValue().length(), 5);
        bs.write(entry.getValue());
      }
      int after = bs.bitsWritten();
      System.err.println("Huffman tree required " + ((after - before)/8) + " bytes");
    }

    static Huffman read(BitReader br) throws IOException {
      int maximumNumberOfBits = br.read(5);
      int numberOfEncodings = br.read(8);
      System.err.println("max bits: " + maximumNumberOfBits);
      System.err.println("number of encodings: " + numberOfEncodings);
      Map<String, Integer> decodings = new HashMap<>();
      for (int i = 0; i < numberOfEncodings; i++) {
        int key = br.read(maximumNumberOfBits);
        int encodedBits = br.read(5);
        StringBuilder sb = new StringBuilder();
        for (int b = 0; b < encodedBits; b++) {
          sb.append(br.read());
        }
        decodings.put(sb.toString(), key);
        System.err.printf("%d: %s%n", key, sb.toString());
      }
      return new Huffman(maximumNumberOfBits, decodings);
    }

    String encode(int value) {
      return encodings.getOrDefault(value, encodings.get(0));
    }

    // return -1 if not found
    int decode(String s) {
      return decodings.getOrDefault(s, -1);

    }
  }

  // Midi commands
  enum Command {
    HEADER("Header", "00"),
    START_TRACK("Start_track", "00"),
    TITLE_T("Title_t", "00"),
    TEXT_T("Text_t", "00"),
    TIME_SIGNATURE("Time_signature", "00"),
    TEMPO("Tempo", "00"),
    END_TRACK("End_track", "00"),
    KEY_SIGNATURE("Key_signature", "00"),
    CONTROL_C("Control_c", "01"),
    NOTE_ON_C("Note_on_c", "10"),
    NOTE_OFF_C("Note_off_c", "11"),
    END_OF_FILE("End_of_file", "00");

    final String value;
    final String prefix;
    Command(String value, String prefix) {
      this.value = value;
      this.prefix = prefix;
    }
    static Command parse(String s) {
      for (Command c : values()) {
        if (s.startsWith(c.value)) {
          return c;
        }
      }
      return null;
    }
    static Command forPrefix(String s) {
      for (Command c : values()) {
        if (c.prefix.equals(s)) {
          return c;
        }
      }
      return null;
    }

    boolean hasParams() {
      return !"00".equals(prefix);
    }
  }

  // A single line from the csv.
  static class Record {
    final int track;
    final int time;
    final Command cmd;
    // There seem to be several sets of parameters...
    // if cmd.prefix is "00", then just write the line literally
    final String literal; // everything including "cmd, " to end of line
    // For everything else, then we have three 8 bit values, a, b, and c
    // And there are only 200 distinct triples... can I turn those into
    // 24 bit values and huffman encode them? Bet I can!
    final int cparams;

    Record(int track, int time, Command cmd, String literal, int cparams) {
      this.track = track;
      this.time = time;
      this.cmd = cmd;
      this.literal = literal;
      this.cparams = cparams;
    }

    String toLine() {
      StringBuilder sb = new StringBuilder();
      sb.append(track).append(", ");
      sb.append(time).append(", ");
      if (literal == null) {
        sb.append(cmd.value);
        sb.append(", ").append((cparams >> 16) & 0x0ff);
        sb.append(", ").append((cparams >> 8) & 0x0ff);
        sb.append(", ").append(cparams  & 0x0ff);
      } else {
        sb.append(literal);
      }
      return sb.toString();
    }

    Record(String line) {
      int start = 0;
      int cursor = line.indexOf(',', start);
      this.track = Integer.parseInt(line.substring(start, cursor));
      start = cursor + 2;
      cursor = line.indexOf(',', start);
      this.time = Integer.parseInt(line.substring(start, cursor));
      start = cursor + 2;
      cursor = line.indexOf(',', start);
      if (cursor == -1) {
        this.cmd = Command.parse(line.substring(start));
        this.literal = line.substring(start);
        this.cparams = -1;
      } else {
        this.cmd = Command.parse(line.substring(start, cursor));
        if (this.cmd.hasParams()) {
          start = cursor + 2;
          this.literal = null;
          int total = 0;
          cursor = line.indexOf(',', start);
          total = Integer.parseInt(line.substring(start, cursor));
          start = cursor + 2;
          cursor = line.indexOf(',', start);
          total = (total << 8) + Integer.parseInt(line.substring(start, cursor));
          start = cursor + 2;
          total = (total << 8) + Integer.parseInt(line.substring(start));
          this.cparams = total;
        } else {
          this.literal = line.substring(start);
          this.cparams = -1;
        }
      }
    }
  }

  static int encode(List<Record> records, OutputStream os)
      throws IOException {
    //  5 bits to encode maximum number of bits (12 for us)
    //  5 bits to encode number of encodings (21 for us)
    //  followed by that many huffman encodings
    //   1: 0        followed by 12 bits
    //   2: 10       ...
    //   3: 110
    //   4: 111
    //   ...
    //     These are encoded as:
    //       value being encoded as an n bit number (where n is from above)
    //       5 bits representing the length of the encoded string
    //       that many bits of encoding string
    //
    //  Followed by the encoded bit stream...

    BitStream bs = new BitStream(os); // dump to stdout

    List<Integer> timeDeltas = new ArrayList<>();
    timeDeltas.add(0);
    for (int i = 1; i < records.size(); i++) {
      timeDeltas.add(records.get(i).time - records.get(i - 1).time);
    }

    Huffman times = new Huffman(timeDeltas);
    times.write(bs);

    Huffman cparams =
        new Huffman(
            records.stream()
                .filter(r -> r.literal == null)
                .map(r -> r.cparams)
                .collect(Collectors.toList()));
    cparams.write(bs);

    int prevTime = 0;

    System.err.println("Writing " + records.size() + " records");
    bs.write(records.size(), 12); // write the number of records
    for (int i = 0; i < records.size(); i++) {
      Record record = records.get(i);
      bs.write(record.track, 2);
      bs.write(times.encode(timeDeltas.get(i)));
      bs.write(record.cmd.prefix);
      if (record.cmd.hasParams()) {
        bs.write(cparams.encode(record.cparams));
      } else {
        bs.write(record.literal.length(), 6);
        for (int j = 0; j < record.literal.length(); j++) {
          int ch = record.literal.charAt(j);
          bs.write(ch, 7);
        }
      }
    }
    bs.close();

    return bs.bitsWritten();
  }

  static List<Record> decode(InputStream is) throws IOException {
    BitReader br = new BitReader(is);
    List<Record> list = new ArrayList<>();

    System.err.println("Decoding time deltas...");
    Huffman times = Huffman.read(br);
    Huffman cparams = Huffman.read(br);

    int numberOfRecords = br.read(12);

    System.err.println("Loading " + numberOfRecords + " records");
    int track = 0;
    int time = 0;
    for (int r = 0; r < numberOfRecords; r++) {
      int tt = br.read(2);
      if (tt != track) {
        time = 0; // reset time on new track
        track = tt;
      }

      StringBuilder bits = new StringBuilder();
      for (;;) {
        bits.append(br.read());
        int decodedTimeDelta = times.decode(bits.toString());
        if (decodedTimeDelta >= 0) {
          time += decodedTimeDelta;
          break;
        }
      }
      bits.setLength(0);
      bits.append(br.read());
      bits.append(br.read());

      int decodedCparams = -1;
      String decodedLiteral = null;
      Command cmd;
      if ("00".equals(bits.toString())) {
        // then we need to read the command from the literal...
        int literalLen = br.read(6);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < literalLen; i++) {
          sb.append((char) (br.read(7)));
        }
        decodedLiteral = sb.toString();
        cmd = Command.parse(decodedLiteral);
        if (cmd == null) {
          throw new RuntimeException("Failed to parse: " + decodedLiteral);
        }
      } else {
        cmd = Command.forPrefix(bits.toString());
        if (cmd == null) {
          throw new RuntimeException("Failed to parse prefix: " + bits.toString());
        }
        bits.setLength(0);
        for (;;) {
          bits.append(br.read());
          decodedCparams = cparams.decode(bits.toString());
          if (decodedCparams >= 0) {
            break;
          }
        }
      }

      if (cmd == Command.END_OF_FILE) {
        track = 0;
        time = 0;
      }

      Record record = new Record(track, time, cmd, decodedLiteral, decodedCparams);

      list.add(record);
    }

    return list;
  }

  public static void main(String[] args) throws Exception {
    if (args.length > 0 && "-d".equals(args[0])) {
      List<Record> list = decode(System.in);
      for (Record r : list) {
        System.out.println(r.toLine());
      }
    } else {
      Scanner scanner = new Scanner(System.in);
      List<Record> list = new ArrayList<>();
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        list.add(new Record(line));
      }

      OutputStream os = new BufferedOutputStream(System.out);

      System.err.println(encode(list, os));
    }
  }
}
