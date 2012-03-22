package test;

import java.awt.Color;

import javax.swing.BorderFactory;
import javax.swing.JFrame;

import util.plot.ColorBar;
import util.plot.ImageArea;
import util.plot.ImageLayer;
import util.plot.Plot;
import util.plot.colorscheme.Colorful;
import crush.GenericInstrument;
import crush.Instrument;
import crush.astro.AstroMap;
import crush.gui.GridImageLayer;

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
		
		GridImageLayer image = new GridImageLayer(map);
		image.setColorScheme(new Colorful());
		
		final ImageArea<GridImageLayer> imager = new ImageArea<GridImageLayer>();
		imager.setContentLayer(image);
		
		Plot<ImageLayer> plot = new Plot<ImageLayer>();
		plot.setPlotArea(imager);
		plot.setBackground(Color.YELLOW);
		
		ColorBar c = new ColorBar(imager, ColorBar.VERTICAL);
		//c.setRotation(1*Unit.deg);
		c.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
		plot.right.add(c);
		
		//plot.setTransparent(true);
		
		JFrame frame = new JFrame();
		frame.setSize(600, 600);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	
		frame.add(plot, "Center");
	
		frame.setVisible(true);
	
	}
}
