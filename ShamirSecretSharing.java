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

	public static void main(String[] args) throws IOException {
		List<String> files = new ArrayList<>();
		if (args != null && args.length > 0) {
			files.addAll(Arrays.asList(args));
		} else {
			files.add("test1.json");
			files.add("test2.json");
		}
		for (String file : files) {
			TestCase tc = parseTestCase(file);
			BigInteger secret = findSecret(tc.points, tc.k);
			System.out.println("Secret for " + file + ": " + secret);
		}
	}
}