package util.plot;


import util.data.Data2D;

public class Data2DLayer extends ImageLayer.Double {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5873926029025733309L;
	private Data2D image;
	
	public Data2DLayer(Data2D image) {
		this.image = image;
		setData(image.getData());
	}
	
	public Data2D getData2D() { return image; }
	
	@Override
	public double getValue(int i, int j) {
		return image.valueAtIndex(i + getSubarrayOffset().i(), j + getSubarrayOffset().j());
	}
	

}
