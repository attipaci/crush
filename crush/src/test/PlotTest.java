package test;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;

import util.plot.ColorBar;
import util.plot.Data2DLayer;
import util.plot.GridImageLayer;
import util.plot.ImageArea;
import util.plot.ImageLayer;
import util.plot.Plot;
import util.plot.PlotSideRuler;
import util.plot.colorscheme.*;
import crush.GenericInstrument;
import crush.Instrument;
import crush.astro.AstroMap;

public class PlotTest {
	
	public static void main(String[] args) {
		try {
			new PlotTest().test();
		}
		catch(Exception e) { e.printStackTrace(); }
	}
	
	public void test() throws Exception {
		Instrument<?> instrument = new GenericInstrument("generic");
		AstroMap map = new AstroMap("/home/pumukli/data/sharc2/images/VESTA.8293.fits", instrument);
		map.autoCrop();
		
		GridImageLayer image = new GridImageLayer(map.getS2NImage());
		image.setColorScheme(new Temperature());
		
		final ImageArea<GridImageLayer> imager = new ImageArea<GridImageLayer>();
		imager.setContentLayer(image);
		imager.setBorder(BorderFactory.createLineBorder(Color.BLACK));
		
		Plot<ImageLayer> plot = new Plot<ImageLayer>();
		plot.setContent(imager);
		plot.setBackground(Color.WHITE);
		plot.setOpaque(true);
		
		ColorBar c = new ColorBar(imager);
		//c.setRotation(1*Unit.deg);
		plot.right.setCenter(c);

		
		JFrame frame = new JFrame();
		frame.setSize(600, 600);
		frame.setBackground(Color.WHITE);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	
		frame.add(plot, "Center");
	
		frame.setVisible(true);
	
	}
}
