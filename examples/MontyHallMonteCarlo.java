import java.util.Random;

/**
 * Using a Monte Carlo Simulation to prove that it's always better
 * to switch after a goat is revealed.
 * See https://en.wikipedia.org/wiki/Monty_Hall_problem.
 *
 * www.brianziman.com
 */
public class MontyHallMonteCarlo {

  static final Random random = new Random();

  /** Given the choice of keeping or switching, return if you won. */
  static boolean play(boolean keep) {
    int solution = random.nextInt(3); // 0, 1, or 2

    int guess = 0; // we could guess at random, but it doesn't matter

    int revealed = solution == 1 ? 2 : 1;

    if (keep) {
      return guess == solution;
    } else {
      int newGuess = revealed == 1 ? 2 : 1;
      return newGuess == solution;
    }
  }

  public static void main(String[] args) {
    int trials = 1000;
    int keepCount = 0;
    int switchCount = 0;
    for (int i = 0; i < trials; i++) {
      if (play(true)) {
        keepCount++;
      }
      if (play(false)) {
        switchCount++;
      }
    }

    System.out.printf(" Keeping original, won %d / %d trials = %d%%%n",
        keepCount, trials, 100 * keepCount / trials);

    System.out.printf("Switching choices, won %d / %d trials = %d%%%n",
        switchCount, trials, 100 * switchCount / trials);
  }

}
