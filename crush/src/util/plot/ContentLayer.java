package util.plot;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;


public abstract class ContentLayer extends PlotLayer {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5434391089909200423L;

	@Override
	public void defaults() {
		// TODO Auto-generated method stub

	}
	
	public abstract Point2D getCoordinateReference();
	
	public abstract Rectangle2D getCoordinateBounds();
	
	public abstract void initialize();

}
