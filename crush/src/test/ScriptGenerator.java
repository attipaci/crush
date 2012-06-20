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
				{ "-rounds=10", "-rounds=20" },
				{ "-stability=3.0", "-stability=5.0", "-stability=10.0" },
				{ "-filter.motion.above=0.05", "-filter.motion.above=0.1", "-filter.motion.above=0.2" },
				{ "-source.filter.fwhm=35", "-source.filter.fwhm=45", "-source.filter.fwhm=60" }
		};
	
		String[][] names = {
				{ "r10", "r20" },
				{ "S3", "S5", "S10" },
				{ "MF005", "MF010", "MF020" },
				{ "x35", "x45", "x60" },
		};
		
		// 2 x 3 x 3 x 3 = 54
		
		System.out.println("CRUSH=\"$HOME/src/crush/crush\"");
		System.out.println("REDUCE=\"./GDF-generic.sh\"");
		System.out.println("GLOBALOPTS=\"\"");
		System.out.println();
		
		int[] index = new int[options.length];
		boolean finished = false;
	
		while(!finished) {		
			String option = "";
			String name = "GDF";
		
			for(int i=0; i<options.length; i++) if(options[i][index[i]] != null) {
				option += options[i][index[i]] + " ";
				name += "." + names[i][index[i]];
			}

		
			name += ".fits";
		
			System.out.println("OPTIONS=\"$GLOBALOPTS " + option.trim() + "\"");
			System.out.println("NAME=\"" + name + "\"");
			System.out.println("source $REDUCE");
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
