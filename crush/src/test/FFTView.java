package test;

import java.io.IOException;

import jnum.Constant;
import jnum.ExtraMath;
import jnum.data.image.CartesianGrid2D;
import jnum.data.image.GridImage2D;
import jnum.data.image.GridMap2D;
import jnum.fft.MultiFFT;
import jnum.math.Complex;
import jnum.math.Coordinate2D;
import jnum.math.Vector2D;
import nom.tam.fits.FitsException;
import nom.tam.fits.HeaderCardException;

public class FFTView {	
	GridImage2D<Coordinate2D> amplitudeImage;
	GridImage2D<Coordinate2D> phaseImage;
		
	double[][] w;
	double wnorm = 0.0;
	
	public static void main(String[] args) {
		try {
			GridMap2D<?> image = new GridMap2D<Coordinate2D>(args[0]);
			double beamCorrection = Double.NaN;
			double norm = 1.0;
			
			if(args.length > 1) {
				beamCorrection = Double.parseDouble(args[1]);
				if(beamCorrection <= 0.0) beamCorrection = Double.NaN;
			}
			if(args.length > 2) {
				norm = Double.parseDouble(args[2]);
			}
					
			FFTView transfer = FFTView.fromImage(image, image.getWeights(), beamCorrection, norm);	
			transfer.write("fft");
			
			if(args.length > 3) {
				GridMap2D<Coordinate2D> map;
				map = new GridMap2D<Coordinate2D>(args[3]);
				FFTView spectrum = FFTView.fromImage(map.getFluxImage(), map.getWeights(), Double.NaN, Double.NaN);
				spectrum.deconvolve(transfer);
				map.setImage(spectrum.backTransform(map.sizeX(), map.sizeY()));			
				map.write("deconvolved.fits");	
			}
			
			
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	
	public void write(String nameStem) throws HeaderCardException, FitsException, IOException {
		amplitudeImage.write(nameStem + ".A.fits");
		phaseImage.write(nameStem + ".phi.fits");
	}
	
	
	public static FFTView fromImage(GridImage2D<?> image, double[][] w, double beamCorrection, double renorm) {
		final int nx = ExtraMath.pow2ceil(image.sizeX());
		final int ny = ExtraMath.pow2ceil(image.sizeY());
		
		Complex[][] point = new Complex[nx][ny];
		
		// Load the image into a padded complex array
		Complex[][] data = new Complex[nx][ny];
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {
			data[i][j] = new Complex();
			point[i][j] = new Complex();
		}

		double sigma = image.getImageBeam().getCircularEquivalentFWHM() / Constant.sigmasInFWHM;
		double sigmax = sigma / image.getResolution().x(); 
		double sigmay = sigma / image.getResolution().y(); 

		
		int midx = image.sizeX() >> 1;
		int midy = image.sizeY() >> 1;
		
		double midw = w[midx][midy];
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			data[i][j].setX(image.get(i, j));	
		}

		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			double devx = (i - midx) / sigmax;
			double devy = (j - midy) / sigmay;
			point[i][j].setX(Math.exp(-0.5 * (devx * devx + devy * devy)));			
		}
		
		
		System.err.println(" Mean level.");
		double sum = 0.0, sumw = 0.0;
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			sum += w[i][j] * data[i][j].x();
			sumw += w[i][j];
		}
		final double mean = sum / sumw;
		
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) 
			data[i][j].subtractX(mean);
			

		
		
		System.err.println(" Remove gradient");
		
		double sumX = 0.0, sumY = 0.0;
		double sumXG2 = 0.0, sumYG2 = 0.0;
		
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			sumX += w[i][j] * (i - midx) * data[i][j].x();
			sumXG2 += w[i][j] * (i - midx) * (i - midx);
			
			sumY += w[i][j] * (j - midy) * data[i][j].x();
			sumYG2 += w[i][j] * (j - midy) * (j - midy);
		}
		double gx = sumX / sumXG2;
		double gy = sumY / sumYG2;
		
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; )
			data[i][j].subtractX(gx * (i - midx) + gy * (j - midy));
	
		
		
		
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			double iN = Math.sqrt(w[i][j] / midw);
			data[i][j].scaleX(iN);
			point[i][j].scaleX(iN);
		}
		
		
		
		
		final int nx2 = nx >> 1;
		final int ny2 = ny >> 1;
		
		// Perform the FFT
		MultiFFT fft = new MultiFFT();
		
		System.err.println(" FFT");
		fft.complexForward(data);
		fft.complexForward(point);
		data[0][0].zero();
		
		FFTView view = new FFTView();

		//final double beamPixels = image.getSmoothArea() / image.getPixelArea();
		//final double norm = Double.isNaN(renorm) ? 1.0 : 0.25 / renorm / beamPixels;
		
		double[][] A = new double[nx][ny];
		double[][] phi = new double[nx][ny];
		double[][] psf = new double[nx][ny];
		
		// Renormalize and unpack into a view with the zero frequency at the center.
		System.err.println(" Renormalize");
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {
			//data[i][j].scale(norm);
			
			int i1 = i - nx2;
			if(i1 < 0) i1 += nx;
			
			int j1 = j - ny2;
			if(j1 < 0) j1 += ny;
			
			A[i1][j1] = data[i][j].abs();
			psf[i1][j1] = point[i][j].abs();
			phi[i1][j1] = data[i][j].angle();
			if(Double.isNaN(phi[i1][j1])) phi[i1][j1] = 0.0;
		}
	
		
		// Create the FFT grid.
		CartesianGrid2D grid = new CartesianGrid2D();
		grid.setReference(new Coordinate2D());
		grid.setReferenceIndex(new Vector2D(nx2, ny2));
		
		Vector2D delta = image.getGrid().getResolution();
		grid.setResolution(Math.PI / 180.0 / (nx * delta.x()), Math.PI / 180.0 / (ny * delta.y()));
	
		if(!Double.isNaN(beamCorrection)) {
			//double sigmaw = 1.0 / (beamCorrection * image.getImageBeam().getCircularEquivalentFWHM() / Constant.sigmasInFWHM);

			//double dfx = 1.0 / (nx * image.getResolution().x());		
			//double sigmafx = sigmaw / (Constant.twoPi * dfx);

			//double dfy = 1.0 / (ny * image.getResolution().y());	
			//double sigmafy = sigmaw / (Constant.twoPi * dfy);

			for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {		
				//double devx = (i - nx2) / sigmafx;
				//double devy = (j - ny2) / sigmafy;
				//double T = Math.exp(-0.5 * (devx * devx + devy * devy)); 

				//A[i][j] = T;
				
				double T = renorm * psf[i][j];
				if(psf[i][j] > 1.0) A[i][j] /= T;
				
				//if(T > 0.01) A[i][j] /= T;
				else A[i][j] = Double.NaN;
			}
		}
					
		// create A, phi images...
		view.amplitudeImage = new GridImage2D<Coordinate2D>();
		view.amplitudeImage.setImage(A);
		view.amplitudeImage.createDefaultFlag();
		view.amplitudeImage.setGrid(grid);
		
		view.phaseImage = new GridImage2D<Coordinate2D>();
		view.phaseImage.setImage(phi);
		view.phaseImage.createDefaultFlag();
		view.phaseImage.setGrid(grid);
		
		view.wnorm = midw;
		view.w = w;
		
		return view;
	}
	
	
	public void deconvolve(FFTView transfer) {
		final int nx = amplitudeImage.sizeX();
		final int ny = amplitudeImage.sizeY();
			
		Vector2D index = new Vector2D();
		Vector2D offset = new Vector2D();
			
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {
			index.set(i, j);
			amplitudeImage.indexToOffset(index, offset);
			transfer.amplitudeImage.offsetToIndex(offset, index);

			// Round the indices...
			index.set((int) Math.round(index.x()), (int) Math.round(index.y()));
			
			double tA = transfer.amplitudeImage.valueAtIndex(index);
			
			if(Double.isNaN(tA)) continue;
			if(tA < 0.01) continue;
			
			amplitudeImage.set(i, j, amplitudeImage.valueAtIndex(i, j) / tA);
			
			//double tPhi = transfer.phaseImage.valueAtIndex(index);
			//phaseImage.setValue(i, j, phaseImage.valueAtIndex(i, j) - tPhi);
		}
		
		
	}
	
	public double[][] backTransform(int sizeX, int sizeY) {
		final int nx = amplitudeImage.sizeX();
		final int ny = amplitudeImage.sizeY();
		
		final int nx2 = nx >> 1;
		final int ny2 = ny >> 1;
			
		Complex[][] spec = new Complex[nx][ny];
		double norm = 1.0 / (nx * ny);
	
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {
			double A = norm * amplitudeImage.valueAtIndex(i, j);
			double phi = phaseImage.valueAtIndex(i, j);
			
			int i1 = i - nx2;
			if(i1 < 0) i1 += nx;
			
			int j1 = j - ny2;
			if(j1 < 0) j1 += ny;
			
			spec[i1][j1] = new Complex(A * Math.cos(phi), A * Math.sin(phi));
		}
		
		MultiFFT fft = new MultiFFT();
		fft.complexBack(spec);
		
		double[][] image = new double[sizeX][sizeY];
		for(int i=Math.min(nx, sizeX); --i >= 0; ) for(int j=Math.min(ny, sizeY); --j >= 0; ) 
			image[i][j] = spec[i][j].x() * Math.sqrt(wnorm / w[i][j]);
		
		return image;
	}
	
	
	
}
