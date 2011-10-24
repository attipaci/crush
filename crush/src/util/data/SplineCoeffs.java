package util.data;

import java.util.Arrays;

public class SplineCoeffs {
	public double[] coeffs = new double[4];

	public void clear() {
		Arrays.fill(coeffs, 0.0);
	}
}
