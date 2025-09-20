import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

public class ShamirSecretSharing {

	public static class Point {
		public BigInteger x;
		public BigInteger y;
		public Point(BigInteger x, BigInteger y) {
			this.x = x;
			this.y = y;
		}
	}

	public static class TestCase {
		public int n;
		public int k;
		public List<Point> points;
		public TestCase() {
			this.points = new ArrayList<>();
		}
	}

	public static TestCase parseTestCase(String filename) throws IOException {
		TestCase testCase = new TestCase();
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line);
			}
		}
		Pattern nPattern = Pattern.compile("\"n\":\\s*(\\d+)");
		Pattern kPattern = Pattern.compile("\"k\":\\s*(\\d+)");
		String contentStr = content.toString();
		Matcher nMatcher = nPattern.matcher(contentStr);
		Matcher kMatcher = kPattern.matcher(contentStr);
		if (nMatcher.find()) {
			testCase.n = Integer.parseInt(nMatcher.group(1));
		}
		if (kMatcher.find()) {
			testCase.k = Integer.parseInt(kMatcher.group(1));
		}
		Pattern pointPattern = Pattern.compile("\"(\\d+)\":\\s*\\{[^}]*\"base\":\\s*\"(\\d+)\"[^}]*\"value\":\\s*\"([^\\\"]+)\"[^}]*\\}");
		Matcher pointMatcher = pointPattern.matcher(contentStr);
		while (pointMatcher.find()) {
			BigInteger x = new BigInteger(pointMatcher.group(1));
			int base = Integer.parseInt(pointMatcher.group(2));
			String value = pointMatcher.group(3);
			BigInteger y = decodeFromBase(value, base);
			testCase.points.add(new Point(x, y));
		}
		return testCase;
	}

	public static BigInteger decodeFromBase(String value, int base) {
		BigInteger result = BigInteger.ZERO;
		BigInteger baseBI = BigInteger.valueOf(base);
		for (int i = 0; i < value.length(); i++) {
			char digit = value.charAt(i);
			int digitValue;
			if (digit >= '0' && digit <= '9') {
				digitValue = digit - '0';
			} else if (digit >= 'a' && digit <= 'z') {
				digitValue = digit - 'a' + 10;
			} else if (digit >= 'A' && digit <= 'Z') {
				digitValue = digit - 'A' + 10;
			} else {
				throw new IllegalArgumentException("Invalid digit: " + digit);
			}
			if (digitValue >= base) {
				throw new IllegalArgumentException("Digit " + digit + " is invalid for base " + base);
			}
			result = result.multiply(baseBI).add(BigInteger.valueOf(digitValue));
		}
		return result;
	}

	// Rational with BigInteger numerator/denominator for exact arithmetic
	public static class Rat {
		BigInteger n; // numerator
		BigInteger d; // denominator (always > 0)

		Rat(BigInteger n, BigInteger d) {
			if (d.signum() == 0) throw new ArithmeticException("Zero denominator");
			// normalize sign to denominator positive
			if (d.signum() < 0) {
				n = n.negate();
				d = d.negate();
			}
			// reduce
			BigInteger g = n.gcd(d);
			if (!g.equals(BigInteger.ONE)) {
				n = n.divide(g);
				d = d.divide(g);
			}
			this.n = n;
			this.d = d;
		}

		static Rat of(BigInteger n) { return new Rat(n, BigInteger.ONE); }

		Rat add(Rat o) {
			BigInteger nn = this.n.multiply(o.d).add(o.n.multiply(this.d));
			BigInteger dd = this.d.multiply(o.d);
			return new Rat(nn, dd);
		}

		Rat mul(Rat o) {
			// cross-reduce to limit growth
			BigInteger g1 = this.n.gcd(o.d);
			BigInteger g2 = o.n.gcd(this.d);
			BigInteger nn = this.n.divide(g1).multiply(o.n.divide(g2));
			BigInteger dd = this.d.divide(g2).multiply(o.d.divide(g1));
			return new Rat(nn, dd);
		}

		Rat div(Rat o) { return this.mul(new Rat(o.d, o.n)); }

		BigInteger toIntegerExact() {
			if (!this.d.equals(BigInteger.ONE)) {
				// ensure exact divisibility
				if (this.n.mod(this.d).signum() != 0) {
					throw new ArithmeticException("Result is not an integer: " + this.n + "/" + this.d);
				}
				return this.n.divide(this.d);
			}
			return this.n;
		}
	}

	public static BigInteger findSecret(List<Point> points, int k) {
		if (points.size() < k) throw new IllegalArgumentException("Not enough points");
		List<Point> selectedPoints = points.subList(0, k);
		Rat sum = Rat.of(BigInteger.ZERO);
		for (int i = 0; i < selectedPoints.size(); i++) {
			Point pi = selectedPoints.get(i);
			// weight for f(0) using Lagrange basis L_i(0) = prod_{j!=i} (0 - x_j)/(x_i - x_j)
			Rat weight = Rat.of(BigInteger.ONE);
			for (int j = 0; j < selectedPoints.size(); j++) {
				if (i == j) continue;
				Point pj = selectedPoints.get(j);
				BigInteger num = pj.x.negate(); // (0 - x_j)
				BigInteger den = pi.x.subtract(pj.x); // (x_i - x_j)
				weight = weight.mul(new Rat(num, den));
			}
			Rat term = Rat.of(pi.y).mul(weight);
			sum = sum.add(term);
		}
		return sum.toIntegerExact();
	}

	// Modular (finite field) variant of secret reconstruction: f(0) mod p
	public static BigInteger findSecretMod(List<Point> points, int k, BigInteger p) {
		if (points.size() < k) throw new IllegalArgumentException("Not enough points");
		List<Point> selectedPoints = points.subList(0, k);
		BigInteger sum = BigInteger.ZERO;
		for (int i = 0; i < selectedPoints.size(); i++) {
			Point pi = selectedPoints.get(i);
			BigInteger weight = BigInteger.ONE;
			for (int j = 0; j < selectedPoints.size(); j++) {
				if (i == j) continue;
				Point pj = selectedPoints.get(j);
				BigInteger num = pj.x.negate().mod(p); // (0 - x_j) mod p
				BigInteger den = pi.x.subtract(pj.x).mod(p); // (x_i - x_j) mod p
				// multiply by num * inv(den) mod p
				weight = weight.multiply(num).mod(p)
						.multiply(den.modInverse(p)).mod(p);
			}
			BigInteger term = pi.y.mod(p).multiply(weight).mod(p);
			sum = sum.add(term).mod(p);
		}
		return sum.mod(p);
	}

	private static BigInteger choosePrimeAbove(List<Point> points) {
		BigInteger max = BigInteger.ZERO;
		for (Point pt : points) {
			if (pt.x.compareTo(max) > 0) max = pt.x;
			if (pt.y.compareTo(max) > 0) max = pt.y;
		}
		// ensure prime > max by some margin
		BigInteger candidate = max.add(BigInteger.valueOf(100));
		return candidate.nextProbablePrime();
	}

	public static void main(String[] args) throws IOException {
		boolean useMod = false;
		BigInteger pOverride = null;
		List<String> files = new ArrayList<>();
		if (args != null && args.length > 0) {
			for (int idx = 0; idx < args.length; idx++) {
				String a = args[idx];
				if ("--mod".equals(a) || "-m".equals(a)) {
					useMod = true;
				} else if (a.startsWith("--prime=")) {
					String val = a.substring("--prime=".length());
					pOverride = new BigInteger(val);
					useMod = true;
				} else if ("--prime".equals(a) || "-p".equals(a)) {
					if (idx + 1 < args.length) {
						pOverride = new BigInteger(args[++idx]);
						useMod = true;
					} else {
						throw new IllegalArgumentException("--prime requires a numeric argument");
					}
				} else {
					files.add(a);
				}
			}
		}
		if (files.isEmpty()) {
			files.add("test1.json");
			files.add("test2.json");
		}
		for (String file : files) {
			TestCase tc = parseTestCase(file);
			if (!useMod) {
				BigInteger secret = findSecret(tc.points, tc.k);
				System.out.println("Secret for " + file + ": " + secret);
			} else {
				BigInteger p = (pOverride != null) ? pOverride : choosePrimeAbove(tc.points);
				BigInteger secret = findSecretMod(tc.points, tc.k, p);
				System.out.println("Secret (mod p) for " + file + ": " + secret + "  where p=" + p);
			}
		}
	}
}