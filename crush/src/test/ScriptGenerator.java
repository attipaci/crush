package test;

public class ScriptGenerator {

	public static void main(String[] args) {
		/*
		String[][] options = {
				{ null, "-estimator=maximum-likelihood" },
				{ "-rounds=10", "-rounds=20", "-rounds=50" },
				{ "-stability=5.0", "-stability=10.0" },
				{ null, "-iteration.[3]despike2", "-iteration.[5]despike2" },
				{ "-source.filter.fwhm=35", "-source.filter.fwhm=40", "-source.filter.fwhm=45" }
				
		};
	
		String[][] names = {
				{ null, "ML" },
				{ "r10", "r20", "r50" },
				{ "S5", "S10" },
				{ null, "d2i3", "d2i5" },
				{ "x35", "x40", "x45" },
		};
		
		// 2 x 3 x 2 x 3 x 3 = 112
		*/
		
		String[][] options = {
				{ "-estimator=maximum-likelihood" },
				{ "-rounds=20", "-rounds=25", "-rounds=30" },
				{ "-stability=2.5", "-stability=5.0" },
				{ "-source.filter.fwhm=30", "-source.filter.fwhm=35", "-source.filter.fwhm=40" }
				
		};
	
		String[][] names = {
				{ "ML" },
				{ "r20", "r25", "r30" },
				{ "S3", "S5" },
				{ "x30", "x35", "x40" },
		};
		
		// 1 x 3 x 2 x 3 = 18
		
		
		int[] index = new int[options.length];
		boolean finished = false;
	
		while(!finished) {		
			String option = "";
			String name = "HDF";
		
			for(int i=0; i<options.length; i++) if(options[i][index[i]] != null) {
				option += options[i][index[i]] + " ";
				name += "." + names[i][index[i]];
			}

		
			name += ".fits";
		
			System.out.println("OPTIONS=\"" + option.trim() + "\"");
			System.out.println("NAME=\"" + name + "\"");
			System.out.println("source ./HDF-generic.sh");
			System.out.println();
			
			boolean overflow = true;
			int n = index.length - 1;
			while(overflow) {
				index[n]++;
				if(index[n] >= options[n].length) {
					index[n] = 0;
					n--;
				}
				else overflow = false;
				
				if(n < 0) {
					overflow = false;
					finished = true;
				}
			}
		}
			
	}
}
