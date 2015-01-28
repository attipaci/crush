package test;

import java.io.IOException;

import crush.astro.AstroMap;
import nom.tam.fits.FitsException;
import nom.tam.fits.HeaderCardException;
import kovacs.data.CartesianGrid2D;
import kovacs.data.GridImage;
import kovacs.data.GridMap;
import kovacs.data.Index2D;
import kovacs.fft.MultiFFT;
import kovacs.math.Complex;
import kovacs.math.Coordinate2D;
import kovacs.math.Vector2D;
import kovacs.util.ExtraMath;
import kovacs.util.Unit;

public class FFTView {	
	GridImage<Coordinate2D> amplitudeImage;
	GridImage<Coordinate2D> phaseImage;
	
	
	public static void main(String[] args) {
		try {
			GridImage<?> image = new GridImage<Coordinate2D>(args[0]);
			double beamCorrection = Double.NaN;
			double norm = 1.0;
			
			if(args.length > 1) {
				beamCorrection = Double.parseDouble(args[1]);
				if(beamCorrection <= 0.0) beamCorrection = Double.NaN;
			}
			if(args.length > 2) {
				norm = Double.parseDouble(args[2]);
			}
			
			
			FFTView transfer = FFTView.fromImage(image, beamCorrection, norm);	
			transfer.write("fft");
			
			if(args.length > 3) {
				GridMap<Coordinate2D> map;
				map = new GridMap<Coordinate2D>(args[3]);
				FFTView spectrum = FFTView.fromImage(map.getFluxImage(), Double.NaN, Double.NaN);
				spectrum.deconvolve(transfer);
				map.setData(spectrum.backTransform(map.sizeX(), map.sizeY()));			
				map.write("deconvolved.fits");
				
			}
			
			
		}
		catch(Exception e) { e.printStackTrace(); }
		
	}
	
	
	public void write(String nameStem) throws HeaderCardException, FitsException, IOException {
		amplitudeImage.write(nameStem + ".A.fits");
		phaseImage.write(nameStem + ".phi.fits");
	}
	
	
	public static FFTView fromImage(GridImage<?> image, double beamCorrection, double renorm) {
		final int nx = ExtraMath.pow2ceil(image.sizeX());
		final int ny = ExtraMath.pow2ceil(image.sizeY());
		
		// Load the image into a padded complex array
		Complex[][] data = new Complex[nx][ny];
		
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) data[i][j] = new Complex();

		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j))
			data[i][j].setX(image.getValue(i, j));

		System.err.println(" Mean level.");
		double sum = 0.0;
		int n = 0;
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			sum += data[i][j].x();
			n++;
		}
		double mean = sum / n;
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) data[i][j].subtractX(mean);
	
		
		final int midx = image.sizeX() >> 1;
		final int midy = image.sizeY() >> 1;
	
	
		System.err.println(" Remove gradient");
		double sumX = 0.0, sumY = 0.0;
		double sumXG2 = 0.0, sumYG2 = 0.0;
		for(int i=image.sizeX(); --i >= 0; ) for(int j=image.sizeY(); --j >= 0; ) if(image.isUnflagged(i, j)) {
			sumX += (i - midx) * data[i][j].x();
			sumXG2 += (i - midx) * (i - midx);
			
			sumY += (j - midy) * data[i][j].x();
			sumYG2 += (j - midy) * (j - midy);
			n++;
		}
		double gx = sumX / sumXG2;
		double gy = sumY / sumYG2;
		
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ){
			data[i][j].subtractX(gx * (i - midx) + gy * (j - midy));
		}
		
	
		final int nx2 = nx >> 1;
		final int ny2 = ny >> 1;
		

		
		// Perform the FFT
		MultiFFT fft = new MultiFFT();
		
		System.err.println(" FFT");
		fft.complexForward(data);
		
		FFTView view = new FFTView();

		final double beamPixels = image.getSmoothArea() / image.getPixelArea();
		final double norm = Double.isNaN(renorm) ? 1.0 : 0.25 / renorm / beamPixels;
		
		double[][] A = new double[nx][ny];
		double[][] phi = new double[nx][ny];
		
		// Renormalize and unpack into a view with the zero frequency at the center.
		System.err.println(" Renormalize");
		for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {
			data[i][j].scale(norm);
			
			int i1 = i - nx2;
			if(i1 < 0) i1 += nx;
			
			int j1 = j - ny2;
			if(j1 < 0) j1 += ny;
			
			A[i1][j1] = data[i][j].abs();
			phi[i1][j1] = data[i][j].angle();
		}
		
		// Create the FFT grid.
		CartesianGrid2D grid = new CartesianGrid2D();
		grid.setReference(new Coordinate2D());
		grid.setReferenceIndex(new Vector2D(nx2, ny2));
		
		Vector2D delta = image.getGrid().getResolution();
		grid.setResolution(Math.PI / 180.0 / (nx * delta.x()), Math.PI / 180.0 / (ny * delta.y()));
	
		if(!Double.isNaN(beamCorrection)) {
			double sigma = beamCorrection * image.getSmoothFWHM() / 2.35;

			double dfx = 2.0 / (nx * image.getResolution().x());		
			double sigmafx = 1.0 / sigma / (2.0 * Math.PI) / dfx;

			double dfy = 2.0 / (ny * image.getResolution().y());	
			double sigmafy = 1.0 / sigma / (2.0 * Math.PI) / dfy;

			for(int i=nx; --i >= 0; ) for(int j=ny; --j >= 0; ) {		
				double w = Math.hypot((i - nx2) / sigmafx, (j - ny2) / sigmafy);
				double T = Math.exp(-0.5 * w * w); 

				//A[i][j] = T;

				if(T > 0.1) A[i][j] /= T;
				else A[i][j] = Double.NaN;

			}
		}
					
		// create A, phi images...
		view.amplitudeImage = new GridImage<Coordinate2D>();
		view.amplitudeImage.setData(A);
		view.amplitudeImage.createDefaultFlag();
		view.amplitudeImage.setGrid(grid);
		
		view.phaseImage = new GridImage<Coordinate2D>();
		view.phaseImage.setData(phi);
		view.phaseImage.createDefaultFlag();
		view.phaseImage.setGrid(grid);
		
		return view;
	}
	
	
	public void deconvolve(FFTView transfer) {
		final int nx = amplitudeImage.sizeX();
		final int ny = amplitudeImage.sizeY();
			
		Vector2D index = new Vector2D();
		Vector2D offset = new Vector2D();
		
		for(int i=nx; --i >=0; ) for(int j=ny; --j >=0; ) {
			index.set(i, j);
			amplitudeImage.indexToOffset(index, offset);
			transfer.amplitudeImage.offsetToIndex(offset, index);

			// Round the indices...
			index.set((int) Math.round(index.x()), (int) Math.round(index.y()));
			
			double tA = transfer.amplitudeImage.valueAtIndex(index);
			if(Double.isNaN(tA)) continue;
			if(tA < 0.01) continue;
			
			double tPhi = transfer.phaseImage.valueAtIndex(index);
			
			amplitudeImage.setValue(i, j, amplitudeImage.valueAtIndex(i, j) / tA);
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
			image[i][j] = spec[i][j].x();
		
		return image;
	}
	
	
	
}
