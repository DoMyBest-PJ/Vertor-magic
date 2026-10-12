/*
	ImageTracer.java
	(Desktop version with javax.imageio. See ImageTracerAndroid.java for the Android version.)
	Simple raster image tracer and vectorizer written in Java. This is a port of imagetracer.js.
	by András Jankovics 2015, 2016
	andras@jankovics.net

 */

/*

The Unlicense / PUBLIC DOMAIN

This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to http://unlicense.org/

 */
package se233.vector_magic;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.imageio.ImageIO;

public class ImageTracer{

	public static String versionnumber = "1.1.2";

	public ImageTracer(){}

	public static void main (String[] args){
		try{

			if(args.length<1){
				System.out.println("ERROR: there's no input filename. Basic usage: \r\n\r\njava -jar ImageTracer.jar <filename>"+
						"\r\n\r\nor\r\n\r\njava -jar ImageTracer.jar help");
			} else if(arraycontains(args,"help")>-1){
				System.out.println("Example usage:\r\n\r\njava -jar ImageTracer.jar <filename> outfilename test.svg "+
						"ltres 1 qtres 1 pathomit 8 colorsampling 1 numberofcolors 16 mincolorratio 0.02 colorquantcycles 3 "+
						"scale 1 simplifytolerance 0 roundcoords 1 lcpr 0 qcpr 0 desc 1 viewbox 0 blurradius 0 blurdelta 20 \r\n"+
						"\r\nOnly <filename> is mandatory, if some of the other optional parameters are missing, they will be set to these defaults. "+
						"\r\nWarning: if outfilename is not specified, then <filename>.svg will be overwritten."+
						"\r\nSee https://github.com/jankovicsandras/imagetracerjava for details. \r\nThis is version "+versionnumber);
			} else {

				// Parameter parsing
				String outfilename = args[0]+".svg";
				HashMap<String,Float> options = new HashMap<String,Float>();
				String[] parameternames = {"ltres","qtres","pathomit","colorsampling","numberofcolors","mincolorratio","colorquantcycles","scale","simplifytolerance","roundcoords","lcpr","qcpr","desc","viewbox","blurradius","blurdelta","outfilename"};
				int j = -1; float f = -1;
				for (String parametername : parameternames) {
					j = arraycontains(args,parametername);
					if(j>-1){
						if(parametername=="outfilename"){
							if( j < (args.length-1)){ outfilename = args[j+1]; }
						}else{
							f = parsenext(args,j); if(f>-1){ options.put(parametername, new Float(f)); }
						}
					}
				}// End of parameternames loop

				// Loading image, tracing, rendering SVG, saving SVG file
				saveString(outfilename,imageToSVG(args[0],options,null));

			}// End of parameter parsing and processing

		}catch(Exception e){ e.printStackTrace(); }
	}// End of main()


	public static int arraycontains (String [] arr, String str){
		for(int j=0; j<arr.length; j++ ){ if(arr[j].toLowerCase().equals(str)){ return j; } } return -1;
	}


	public static float parsenext (String [] arr, int i){
		if(i<(arr.length-1)){ try{ return Float.parseFloat(arr[i+1]); }catch(Exception e){} } return -1;
	}


	// Container for the color-indexed image before and tracedata after vectorizing
	public static class IndexedImage{
		public int width, height;
		public int [][] array; // array[x][y] of palette colors
		public byte [][] palette;// array[palettelength][4] RGBA color palette
		public ArrayList<ArrayList<ArrayList<Double[]>>> layers;// tracedata

		public IndexedImage(int [][] marray, byte [][] mpalette){
			array = marray; palette = mpalette;
			width = marray[0].length-2; height = marray.length-2;// Color quantization adds +2 to the original width and height
		}
	}


	// https://developer.mozilla.org/en-US/docs/Web/API/ImageData
	public static class ImageData{
		public int width, height;
		public byte[] data; // raw byte data: R G B A R G B A ...
		public ImageData(int mwidth, int mheight, byte[] mdata){
			width = mwidth; height = mheight; data = mdata;
		}
	}


	// Saving a String as a file
	public static void saveString (String filename, String str) throws Exception {
		File file = new File(filename);
		// if file doesnt exists, then create it
		if(!file.exists()){ file.createNewFile(); }
		FileWriter fw = new FileWriter(file.getAbsoluteFile());
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write(str);
		bw.close();
	}


	// Loading a file to ImageData, ARGB byte order
	public static ImageData loadImageData (String filename) throws Exception {
		BufferedImage image = ImageIO.read(new File(filename));
		return loadImageData(image);
	}
	public static ImageData loadImageData (BufferedImage image) throws Exception {
		int width = image.getWidth(); int height = image.getHeight();
		int[] rawdata = image.getRGB(0, 0, width, height, null, 0, width);
		byte[] data = new byte[rawdata.length*4];
		for(int i=0; i<rawdata.length; i++){
			data[(i*4)+3] = bytetrans((byte)(rawdata[i] >>> 24));
			data[i*4  ] = bytetrans((byte)(rawdata[i] >>> 16));
			data[(i*4)+1] = bytetrans((byte)(rawdata[i] >>> 8));
			data[(i*4)+2] = bytetrans((byte)(rawdata[i]));
		}
		return new ImageData(width,height,data);
	}


	// The bitshift method in loadImageData creates signed bytes where -1 -> 255 unsigned ; -128 -> 128 unsigned ;
	// 127 -> 127 unsigned ; 0 -> 0 unsigned ; These will be converted to -128 (representing 0 unsigned) ...
	// 127 (representing 255 unsigned) and tosvgcolorstr will add +128 to create RGB values 0..255
	public static byte bytetrans (byte b){
		if(b<0){ return (byte)(b+128); }else{ return (byte)(b-128); }
	}


	////////////////////////////////////////////////////////////
	//
	//  User friendly functions
	//
	////////////////////////////////////////////////////////////

	// Loading an image from a file, tracing when loaded, then returning the SVG String
	public static String imageToSVG (String filename, HashMap<String,Float> options, byte [][] palette) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(filename);
		return imagedataToSVG(imgd,options,palette);
	}// End of imageToSVG()
	public static String imageToSVG (BufferedImage image, HashMap<String,Float> options, byte [][] palette) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(image);
		return imagedataToSVG(imgd,options,palette);
	}// End of imageToSVG()


	// Tracing ImageData, then returning the SVG String
	public static String imagedataToSVG (ImageData imgd, HashMap<String,Float> options, byte [][] palette){
		options = checkoptions(options);
		IndexedImage ii = imagedataToTracedata(imgd,options,palette);
		return getsvgstring(ii, options);
	}// End of imagedataToSVG()


	// Loading an image from a file, tracing when loaded, then returning IndexedImage with tracedata in layers
	public IndexedImage imageToTracedata (String filename, HashMap<String,Float> options, byte [][] palette) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(filename);
		return imagedataToTracedata(imgd,options,palette);
	}// End of imageToTracedata()
	public IndexedImage imageToTracedata (BufferedImage image, HashMap<String,Float> options, byte [][] palette) throws Exception{
		options = checkoptions(options);
		ImageData imgd = loadImageData(image);
		return imagedataToTracedata(imgd,options,palette);
	}// End of imageToTracedata()


	// Tracing ImageData, then returning IndexedImage with tracedata in layers
	public static IndexedImage imagedataToTracedata (ImageData imgd, HashMap<String,Float> options, byte [][] palette){
		options = checkoptions(options);
		// 1. Color quantization
		IndexedImage ii = colorquantization(imgd, palette, options);
		// 2. Layer separation and edge detection
		int[][][] rawlayers = layering(ii);
		// 3. Batch pathscan
		ArrayList<ArrayList<ArrayList<Integer[]>>> bps = batchpathscan(rawlayers,(int)(Math.floor(options.get("pathomit"))));
		// 4. Batch interpollation
		ArrayList<ArrayList<ArrayList<Double[]>>> bis = batchinternodes(bps);
		// 5. Batch tracing
		ii.layers = batchtracelayers(bis,options.get("ltres"),options.get("qtres"));
		return ii;
	}// End of imagedataToTracedata()


	// creating options object, setting defaults for missing values
	public static HashMap<String,Float> checkoptions (HashMap<String,Float> options){
		if(options==null){ options = new HashMap<String,Float>(); }
		// Tracing
		if(!options.containsKey("ltres")){ options.put("ltres",1f); }
		if(!options.containsKey("qtres")){ options.put("qtres",1f); }
		if(!options.containsKey("pathomit")){ options.put("pathomit",8f); }
		// Color quantization
		if(!options.containsKey("colorsampling")){ options.put("colorsampling",1f); }
		if(!options.containsKey("numberofcolors")){ options.put("numberofcolors",16f); }
		if(!options.containsKey("mincolorratio")){ options.put("mincolorratio",0.02f); }
		if(!options.containsKey("colorquantcycles")){ options.put("colorquantcycles",3f); }
		// SVG rendering
		if(!options.containsKey("scale")){ options.put("scale",1f); }
		if(!options.containsKey("simplifytolerance")){ options.put("simplifytolerance",0f); }
		if(!options.containsKey("roundcoords")){ options.put("roundcoords",1f); }
		if(!options.containsKey("lcpr")){ options.put("lcpr",0f); }
		if(!options.containsKey("qcpr")){ options.put("qcpr",0f); }
		if(!options.containsKey("desc")){ options.put("desc",1f); }
		if(!options.containsKey("viewbox")){ options.put("viewbox",0f); }
		// Blur
		if(!options.containsKey("blurradius")){ options.put("blurradius",0f); }
		if(!options.containsKey("blurdelta")){ options.put("blurdelta",20f); }

		return options;
	}// End of checkoptions()


	////////////////////////////////////////////////////////////
	//
	//  Vectorizing functions
	//
	////////////////////////////////////////////////////////////

	// 1. Color quantization repeated "cycles" times, based on K-means clustering
	// https://en.wikipedia.org/wiki/Color_quantization    https://en.wikipedia.org/wiki/K-means_clustering
	public static IndexedImage colorquantization (ImageData imgd, byte [][] palette, HashMap<String,Float> options){
		int numberofcolors = (int)Math.floor(options.get("numberofcolors")); float minratio = options.get("mincolorratio"); int cycles = (int)Math.floor(options.get("colorquantcycles"));
		// Creating indexed color array arr which has a boundary filled with -1 in every direction
		int [][] arr = new int[imgd.height+2][imgd.width+2];
		for(int j=0; j<(imgd.height+2); j++){ arr[j][0] = -1; arr[j][imgd.width+1 ] = -1; }
		for(int i=0; i<(imgd.width+2) ; i++){ arr[0][i] = -1; arr[imgd.height+1][i] = -1; }

		int idx=0, cd,cdl,ci,c1,c2,c3,c4;

		// Use custom palette if pal is defined or sample or generate custom length palette
		if(palette==null){
			if(options.get("colorsampling")!=0){
				palette = samplepalette(numberofcolors,imgd);
			}else{
				palette = generatepalette(numberofcolors);
			}
		}

		// Selective Gaussian blur preprocessing
		if( options.get("blurradius") > 0 ){ imgd = blur( imgd, options.get("blurradius"), options.get("blurdelta") ); }

		long [][] paletteacc = new long[palette.length][5];

		// Repeat clustering step "cycles" times
		for(int cnt=0;cnt<cycles;cnt++){

			// Average colors from the second iteration
			if(cnt>0){
				// averaging paletteacc for palette
				float ratio;
				for(int k=0;k<palette.length;k++){
					// averaging
					if(paletteacc[k][4]>0){
						palette[k][0] = (byte) (-128 + (paletteacc[k][0] / paletteacc[k][4]));
						palette[k][1] = (byte) (-128 + (paletteacc[k][1] / paletteacc[k][4]));
						palette[k][2] = (byte) (-128 + (paletteacc[k][2] / paletteacc[k][4]));
						palette[k][3] = (byte) (-128 + (paletteacc[k][3] / paletteacc[k][4]));
					}
					ratio = (float)( (double)(paletteacc[k][4]) / (double)(imgd.width*imgd.height) );

					// Replenish an under-represented cluster using pixel from source image
					if( (ratio<minratio) && (cnt<(cycles-1)) ){
						int fallbackIdx = (int)(((long) k * 7919) % Math.max(1, imgd.data.length / 4));
						int idxFallback = fallbackIdx * 4;
						palette[k][0] = imgd.data[idxFallback];
						palette[k][1] = imgd.data[idxFallback+1];
						palette[k][2] = imgd.data[idxFallback+2];
						palette[k][3] = imgd.data[idxFallback+3];
					}

				}// End of palette loop
			}// End of Average colors from the second iteration

			// Reseting palette accumulator for averaging
			for(int i=0;i<palette.length;i++){
				paletteacc[i][0]=0;
				paletteacc[i][1]=0;
				paletteacc[i][2]=0;
				paletteacc[i][3]=0;
				paletteacc[i][4]=0;
			}

			// loop through all pixels
			for(int j=0;j<imgd.height;j++){
				for(int i=0;i<imgd.width;i++){

					idx = ((j*imgd.width)+i)*4;

					// find closest color from palette by measuring (rectilinear) color distance between this pixel and all palette colors
					cdl = 256+256+256+256; ci=0;
					for(int k=0;k<palette.length;k++){

						// In my experience, https://en.wikipedia.org/wiki/Rectilinear_distance works better than https://en.wikipedia.org/wiki/Euclidean_distance
						c1 = Math.abs(palette[k][0]-imgd.data[idx]);
						c2 = Math.abs(palette[k][1]-imgd.data[idx+1]);
						c3 = Math.abs(palette[k][2]-imgd.data[idx+2]);
						c4 = Math.abs(palette[k][3]-imgd.data[idx+3]);
						cd = c1+c2+c3+(c4*4); // weighted alpha seems to help images with transparency

						// Remember this color if this is the closest yet
						if(cd<cdl){ cdl = cd; ci = k; }

					}// End of palette loop

					// add to palettacc
					paletteacc[ci][0] += 128+imgd.data[idx];
					paletteacc[ci][1] += 128+imgd.data[idx+1];
					paletteacc[ci][2] += 128+imgd.data[idx+2];
					paletteacc[ci][3] += 128+imgd.data[idx+3];
					paletteacc[ci][4]++;

					arr[j+1][i+1] = ci;
				}// End of i loop
			}// End of j loop

		}// End of Repeat clustering step "cycles" times

		return new IndexedImage(arr, palette);
	}// End of colorquantization


	// Generating a palette with numberofcolors, array[numberofcolors][4] where [i][0] = R ; [i][1] = G ; [i][2] = B ; [i][3] = A
	public static byte[][] generatepalette (int numberofcolors){
		byte [][] palette = new byte[numberofcolors][4];
		if(numberofcolors<8){

			// Grayscale
			double graystep = 255.0/(double)(numberofcolors-1);
			for(byte ccnt=0;ccnt<numberofcolors;ccnt++){
				palette[ccnt][0] = (byte)(-128+Math.round(ccnt*graystep));
				palette[ccnt][1] = (byte)(-128+Math.round(ccnt*graystep));
				palette[ccnt][2] = (byte)(-128+Math.round(ccnt*graystep));
				palette[ccnt][3] = (byte)127;
			}

		}else{

			// RGB color cube
			int colorqnum = (int) Math.floor(Math.pow(numberofcolors, 1.0/3.0)); // Number of points on each edge on the RGB color cube
			int colorstep = (int) Math.floor(255/(colorqnum-1)); // distance between points
			int ccnt = 0;
			for(int rcnt=0;rcnt<colorqnum;rcnt++){
				for(int gcnt=0;gcnt<colorqnum;gcnt++){
					for(int bcnt=0;bcnt<colorqnum;bcnt++){
						palette[ccnt][0] = (byte)(-128+(rcnt*colorstep));
						palette[ccnt][1] = (byte)(-128+(gcnt*colorstep));
						palette[ccnt][2] = (byte)(-128+(bcnt*colorstep));
						palette[ccnt][3] = (byte)127;
						ccnt++;
					}// End of blue loop
				}// End of green loop
			}// End of red loop

			// Rest is random
			for(int rcnt=ccnt;rcnt<numberofcolors;rcnt++){
				palette[ccnt][0] = (byte)(-128+Math.floor(Math.random()*255));
				palette[ccnt][1] = (byte)(-128+Math.floor(Math.random()*255));
				palette[ccnt][2] = (byte)(-128+Math.floor(Math.random()*255));
				palette[ccnt][3] = (byte)(-128+Math.floor(Math.random()*255));
			}

		}// End of numberofcolors check

		return palette;
	};// End of generatepalette()


	public static byte[][] samplepalette (int numberofcolors, ImageData imgd){
		int numPixels = imgd.data.length / 4;
		byte [][] palette = new byte[numberofcolors][4];
		if (numPixels <= 0) return palette;
		long step = Math.max(1, (long) numPixels / numberofcolors);
		long offset = step / 2;
		for(int i=0; i<numberofcolors; i++){
			int pixelIdx = (int) Math.min(numPixels - 1, offset + (i * step));
			int idx = pixelIdx * 4;
			palette[i][0] = imgd.data[idx  ];
			palette[i][1] = imgd.data[idx+1];
			palette[i][2] = imgd.data[idx+2];
			palette[i][3] = imgd.data[idx+3];
		}
		return palette;
	}// End of samplepalette()


	// 2. Layer separation and edge detection
	// Edge node types ( ▓:light or 1; ░:dark or 0 )
	// 12  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓
	// 48  ░░  ░░  ░░  ░░  ░▓  ░▓  ░▓  ░▓  ▓░  ▓░  ▓░  ▓░  ▓▓  ▓▓  ▓▓  ▓▓
	//     0   1   2   3   4   5   6   7   8   9   10  11  12  13  14  15
	//
	public static int[][][] layering (IndexedImage ii){
		// Creating layers for each indexed color in arr
		int val=0, aw = ii.array[0].length, ah = ii.array.length, n1,n2,n3,n4,n5,n6,n7,n8;
		int[][][] layers = new int[ii.palette.length][ah][aw];

		// Looping through all pixels and calculating edge node type
		for(int j=1; j<(ah-1); j++){
			for(int i=1; i<(aw-1); i++){

				// This pixel's indexed color
				val = ii.array[j][i];

				// Are neighbor pixel colors the same?
				n1 = ii.array[j-1][i-1]==val ? 1 : 0;
				n2 = ii.array[j-1][i  ]==val ? 1 : 0;
				n3 = ii.array[j-1][i+1]==val ? 1 : 0;
				n4 = ii.array[j  ][i-1]==val ? 1 : 0;
				n5 = ii.array[j  ][i+1]==val ? 1 : 0;
				n6 = ii.array[j+1][i-1]==val ? 1 : 0;
				n7 = ii.array[j+1][i  ]==val ? 1 : 0;
				n8 = ii.array[j+1][i+1]==val ? 1 : 0;

				// this pixel"s type and looking back on previous pixels
				layers[val][j+1][i+1] = 1 + (n5 * 2) + (n8 * 4) + (n7 * 8) ;
				if(n4==0){ layers[val][j+1][i  ] = 0 + 2 + (n7 * 4) + (n6 * 8) ; }
				if(n2==0){ layers[val][j  ][i+1] = 0 + (n3*2) + (n5 * 4) + 8 ; }
				if(n1==0){ layers[val][j  ][i  ] = 0 + (n2*2) + 4 + (n4 * 8) ; }

			}// End of i loop
		}// End of j loop

		return layers;
	}// End of layering()


	// Lookup tables for pathscan
	static byte [] pathscan_dir_lookup = {0,0,3,0, 1,0,3,0, 0,3,3,1, 0,3,0,0};
	static boolean [] pathscan_holepath_lookup = {false,false,false,false, false,false,false,true, false,false,false,true, false,true,true,false };
	// pathscan_combined_lookup[ arr[py][px] ][ dir ] = [nextarrpypx, nextdir, deltapx, deltapy];
	static byte [][][] pathscan_combined_lookup = {
			{{-1,-1,-1,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}},// arr[py][px]==0 is invalid
			{{ 0, 1, 0,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}, { 0, 2,-1, 0}},
			{{-1,-1,-1,-1}, {-1,-1,-1,-1}, { 0, 1, 0,-1}, { 0, 0, 1, 0}},
			{{ 0, 0, 1, 0}, {-1,-1,-1,-1}, { 0, 2,-1, 0}, {-1,-1,-1,-1}},

			{{-1,-1,-1,-1}, { 0, 0, 1, 0}, { 0, 3, 0, 1}, {-1,-1,-1,-1}},
			{{13, 3, 0, 1}, {13, 2,-1, 0}, { 7, 1, 0,-1}, { 7, 0, 1, 0}},
			{{-1,-1,-1,-1}, { 0, 1, 0,-1}, {-1,-1,-1,-1}, { 0, 3, 0, 1}},
			{{ 0, 3, 0, 1}, { 0, 2,-1, 0}, {-1,-1,-1,-1}, {-1,-1,-1,-1}},

			{{ 0, 3, 0, 1}, { 0, 2,-1, 0}, {-1,-1,-1,-1}, {-1,-1,-1,-1}},
			{{-1,-1,-1,-1}, { 0, 1, 0,-1}, {-1,-1,-1,-1}, { 0, 3, 0, 1}},
			{{11, 1, 0,-1}, {14, 0, 1, 0}, {14, 3, 0, 1}, {11, 2,-1, 0}},
			{{-1,-1,-1,-1}, { 0, 0, 1, 0}, { 0, 3, 0, 1}, {-1,-1,-1,-1}},

			{{ 0, 0, 1, 0}, {-1,-1,-1,-1}, { 0, 2,-1, 0}, {-1,-1,-1,-1}},
			{{-1,-1,-1,-1}, {-1,-1,-1,-1}, { 0, 1, 0,-1}, { 0, 0, 1, 0}},
			{{ 0, 1, 0,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}, { 0, 2,-1, 0}},
			{{-1,-1,-1,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}, {-1,-1,-1,-1}}// arr[py][px]==15 is invalid
	};


	// 3. Walking through an edge node array, discarding edge node types 0 and 15 and creating paths from the rest.
	// Walk directions (dir): 0 > ; 1 ^ ; 2 < ; 3 v
	// Edge node types ( ▓:light or 1; ░:dark or 0 )
	// ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓  ░░  ▓░  ░▓  ▓▓
	// ░░  ░░  ░░  ░░  ░▓  ░▓  ░▓  ░▓  ▓░  ▓░  ▓░  ▓░  ▓▓  ▓▓  ▓▓  ▓▓
	// 0   1   2   3   4   5   6   7   8   9   10  11  12  13  14  15
	//
	public static ArrayList<ArrayList<Integer[]>> pathscan (int [][] arr,float pathomit){
		ArrayList<ArrayList<Integer[]>> paths = new ArrayList<ArrayList<Integer[]>>();
		ArrayList<Integer[]> thispath;
		int px=0,py=0,w=arr[0].length,h=arr.length,dir=0;
		boolean pathfinished=true, holepath = false;
		byte[] lookuprow;

		for(int j=0;j<h;j++){
			for(int i=0;i<w;i++){
				if((arr[j][i]!=0)&&(arr[j][i]!=15)){

					// Init
					px = i; py = j;
					paths.add(new ArrayList<Integer[]>());
					thispath = paths.get(paths.size()-1);
					pathfinished = false;

					// fill paths will be drawn, but hole paths are also required to remove unnecessary edge nodes
					dir = pathscan_dir_lookup[ arr[py][px] ]; holepath = pathscan_holepath_lookup[ arr[py][px] ];

					// Path points loop
					while(!pathfinished){

						// New path point
						thispath.add(new Integer[3]);
						thispath.get(thispath.size()-1)[0] = px-1;
						thispath.get(thispath.size()-1)[1] = py-1;
						thispath.get(thispath.size()-1)[2] = arr[py][px];

						// Next: look up the replacement, direction and coordinate changes = clear this cell, turn if required, walk forward
						lookuprow = pathscan_combined_lookup[ arr[py][px] ][ dir ];
						arr[py][px] = lookuprow[0]; dir = lookuprow[1]; px += lookuprow[2]; py += lookuprow[3];

						// Close path
						if(((px-1)==thispath.get(0)[0])&&((py-1)==thispath.get(0)[1])){
							pathfinished = true;
							// Discarding 'hole' type paths and paths shorter than pathomit
							if( (holepath) || (thispath.size()<pathomit) ){
								paths.remove(thispath);
							}
						}

					}// End of Path points loop

				}// End of Follow path

			}// End of i loop
		}// End of j loop

		return paths;
	}// End of pathscan()


	// 3. Batch pathscan
	public static ArrayList<ArrayList<ArrayList<Integer[]>>> batchpathscan (int [][][] layers, float pathomit){
		ArrayList<ArrayList<ArrayList<Integer[]>>> bpaths = new ArrayList<ArrayList<ArrayList<Integer[]>>>();
		for (int[][] layer : layers) {
			bpaths.add(pathscan(layer,pathomit));
		}
		return bpaths;
	}


	// 4. interpolating between path points for nodes with 8 directions ( East, SouthEast, S, SW, W, NW, N, NE )
	public static ArrayList<ArrayList<Double[]>> internodes (ArrayList<ArrayList<Integer[]>> paths){
		ArrayList<ArrayList<Double[]>> ins = new ArrayList<ArrayList<Double[]>>();
		ArrayList<Double[]> thisinp;
		int palen=0;

		// paths loop
		for(int pacnt=0; pacnt<paths.size(); pacnt++){
			ins.add(new ArrayList<Double[]>());
			thisinp = ins.get(ins.size()-1);
			ArrayList<Integer[]> path = paths.get(pacnt);
			palen = path.size();
			if(palen < 3) continue;

			// pathpoints loop
			for(int pcnt=0; pcnt<palen; pcnt++){
				int previdx = (pcnt - 1 + palen) % palen;
				int nextidx = (pcnt + 1) % palen;
				Integer[] ppPrev = path.get(previdx);
				Integer[] ppCur = path.get(pcnt);
				Integer[] ppNext = path.get(nextidx);

				int kLook = Math.min(3, palen / 4);
				int prevK = (pcnt - kLook + palen) % palen;
				int nextK = (pcnt + kLook) % palen;
				Integer[] ppPrevK = path.get(prevK);
				Integer[] ppNextK = path.get(nextK);

				double v1x = ppCur[0] - ppPrevK[0], v1y = ppCur[1] - ppPrevK[1];
				double v2x = ppNextK[0] - ppCur[0], v2y = ppNextK[1] - ppCur[1];
				double len1 = Math.sqrt(v1x * v1x + v1y * v1y);
				double len2 = Math.sqrt(v2x * v2x + v2y * v2y);

				boolean isCorner = false;
				if(len1 > 1e-4 && len2 > 1e-4){
					double cosTheta = (v1x * v2x + v1y * v2y) / (len1 * len2);
					isCorner = (cosTheta < 0.57);
				}

				Double[] pMid = new Double[3];
				pMid[0] = (ppPrev[0] + ppCur[0]) / 2.0;
				pMid[1] = (ppPrev[1] + ppCur[1]) / 2.0;
				pMid[2] = 0.0;
				thisinp.add(pMid);

				if(isCorner){
					Double[] pCorner = new Double[3];
					pCorner[0] = (double) ppCur[0];
					pCorner[1] = (double) ppCur[1];
					pCorner[2] = 0.0;
					thisinp.add(pCorner);
				}
			}

			// calculate 8-direction segment types
			int inplen = thisinp.size();
			for(int i=0; i<inplen; i++){
				Double[] cur = thisinp.get(i);
				Double[] next = thisinp.get((i + 1) % inplen);
				if(cur[0] < next[0]){
					if     (cur[1] < next[1]){ cur[2] = 1.0; } // SouthEast
					else if(cur[1] > next[1]){ cur[2] = 7.0; } // NE
					else                     { cur[2] = 0.0; } // E
				}else if(cur[0] > next[0]){
					if     (cur[1] < next[1]){ cur[2] = 3.0; } // SW
					else if(cur[1] > next[1]){ cur[2] = 5.0; } // NW
					else                     { cur[2] = 4.0; } // W
				}else{
					if     (cur[1] < next[1]){ cur[2] = 2.0; } // S
					else if(cur[1] > next[1]){ cur[2] = 6.0; } // N
					else                     { cur[2] = 8.0; } // center
				}
			}
		}// End of paths loop
		return ins;
	}// End of internodes()


	// 4. Batch interpollation
	static ArrayList<ArrayList<ArrayList<Double[]>>> batchinternodes (ArrayList<ArrayList<ArrayList<Integer[]>>> bpaths){
		ArrayList<ArrayList<ArrayList<Double[]>>> binternodes = new ArrayList<ArrayList<ArrayList<Double[]>>>();
		for(int k=0; k<bpaths.size(); k++) {
			binternodes.add(internodes(bpaths.get(k)));
		}
		return binternodes;
	}


	// 5. tracepath() : recursively trying to fit straight and quadratic spline segments on the 8 direction internode path

	// 5.1. Find sequences of points with only 2 segment types
	// 5.2. Fit a straight line on the sequence
	// 5.3. If the straight line fails (an error>ltreshold), find the point with the biggest error
	// 5.4. Fit a quadratic spline through errorpoint (project this to get controlpoint), then measure errors on every point in the sequence
	// 5.5. If the spline fails (an error>qtreshold), find the point with the biggest error, set splitpoint = (fitting point + errorpoint)/2
	// 5.6. Split sequence and recursively apply 5.2. - 5.7. to startpoint-splitpoint and splitpoint-endpoint sequences
	// 5.7. TODO? If splitpoint-endpoint is a spline, try to add new points from the next sequence

	// This returns an SVG Path segment as a double[7] where
	// segment[0] ==1.0 linear  ==2.0 quadratic interpolation
	// segment[1] , segment[2] : x1 , y1
	// segment[3] , segment[4] : x2 , y2 ; middle point of Q curve, endpoint of L line
	// segment[5] , segment[6] : x3 , y3 for Q curve, should be 0.0 , 0.0 for L line
	//
	// path type is discarded, no check for path.size < 3 , which should not happen

	public static ArrayList<Double[]> tracepath (ArrayList<Double[]> path, float ltreshold, float qtreshold){
		int pcnt=0, seqend=0; double segtype1, segtype2;
		ArrayList<Double[]> smp = new ArrayList<Double[]>();
		//Double [] thissegment;
		int pathlength = path.size();

		while(pcnt<pathlength){
			// 5.1. Find sequences of points with only 2 segment types
			segtype1 = path.get(pcnt)[2]; segtype2 = -1; seqend=pcnt+1;
			while(
					((path.get(seqend)[2]==segtype1) || (path.get(seqend)[2]==segtype2) || (segtype2==-1))
					&& (seqend<(pathlength-1))){
				if((path.get(seqend)[2]!=segtype1) && (segtype2==-1)){ segtype2 = path.get(seqend)[2];}
				seqend++;
			}
			if(seqend==(pathlength-1)){ seqend = 0; }

			// 5.2. - 5.6. Split sequence and recursively apply 5.2. - 5.6. to startpoint-splitpoint and splitpoint-endpoint sequences
			smp.addAll(fitseq(path,ltreshold,qtreshold,pcnt,seqend));
			// 5.7. TODO? If splitpoint-endpoint is a spline, try to add new points from the next sequence

			// forward pcnt;
			if(seqend>0){ pcnt = seqend; }else{ pcnt = pathlength; }

		}// End of pcnt loop

		return smp;

	}// End of tracepath()


	// 5.2. - 5.6. recursively fitting a straight or quadratic line segment on this sequence of path nodes,
	// called from tracepath()
	public static ArrayList<Double[]> fitseq (ArrayList<Double[]> path, float ltreshold, float qtreshold, int seqstart, int seqend){
		ArrayList<Double[]> segment = new ArrayList<Double[]>();
		int pathlength = path.size();

		// return if invalid seqend
		if((seqend>pathlength)||(seqend<0)){return segment;}

		double x1 = path.get(seqstart)[0], y1 = path.get(seqstart)[1];
		double x2 = path.get(seqend)[0], y2 = path.get(seqend)[1];
		double dx = x2 - x1, dy = y2 - y1;
		double lenSq = dx * dx + dy * dy;

		int count = seqend - seqstart;
		if (count < 0) count += pathlength;
		if (count == 0) return segment;

		// Cumulative chord distances along the discrete contour
		double[] cumDist = new double[count + 1];
		cumDist[0] = 0;
		for (int i = 1; i <= count; i++) {
			int pPrev = (seqstart + i - 1) % pathlength;
			int pCur = (seqstart + i) % pathlength;
			double dpx = path.get(pCur)[0] - path.get(pPrev)[0];
			double dpy = path.get(pCur)[1] - path.get(pPrev)[1];
			cumDist[i] = cumDist[i - 1] + Math.sqrt(dpx * dpx + dpy * dpy);
		}
		double totalChord = cumDist[count];
		if (totalChord < 1e-6) totalChord = 1e-6;

		// 5.2. Test straight line fit using true perpendicular point-to-segment distance
		boolean linePass = true;
		int errorpoint = seqstart;
		double maxError = 0.0;
		double ltresSq = (double) ltreshold * (double) ltreshold;

		for (int i = 1; i < count; i++) {
			int pCur = (seqstart + i) % pathlength;
			double px = path.get(pCur)[0], py = path.get(pCur)[1];
			double distSq;
			if (lenSq < 1e-9) {
				distSq = (px - x1) * (px - x1) + (py - y1) * (py - y1);
			} else {
				double u = ((px - x1) * dx + (py - y1) * dy) / lenSq;
				if (u < 0.0) u = 0.0;
				else if (u > 1.0) u = 1.0;
				double projX = x1 + u * dx;
				double projY = y1 + u * dy;
				distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY);
			}

			if (distSq > ltresSq) linePass = false;
			if (distSq > maxError) {
				maxError = distSq;
				errorpoint = pCur;
			}
		}

		// Return straight line if fits
		if (linePass) {
			Double[] seg = new Double[7];
			seg[0] = 1.0;
			seg[1] = x1; seg[2] = y1;
			seg[3] = x2; seg[4] = y2;
			seg[5] = 0.0; seg[6] = 0.0;
			segment.add(seg);
			return segment;
		}

		// 5.3. Straight line failed: quadratic Bézier spline fitting using chord-length parameterization
		int fitpoint = errorpoint;
		int fitIndex = fitpoint - seqstart;
		if (fitIndex < 0) fitIndex += pathlength;
		double tFit = cumDist[fitIndex] / totalChord;
		if (tFit < 0.05) tFit = 0.05;
		if (tFit > 0.95) tFit = 0.95;

		double t1 = (1.0 - tFit) * (1.0 - tFit);
		double t2 = 2.0 * (1.0 - tFit) * tFit;
		double t3 = tFit * tFit;
		double cpx = (((t1 * x1) + (t3 * x2)) - path.get(fitpoint)[0]) / -t2;
		double cpy = (((t1 * y1) + (t3 * y2)) - path.get(fitpoint)[1]) / -t2;

		boolean curvePass = true;
		maxError = 0.0;
		errorpoint = seqstart;
		double qtresSq = (double) qtreshold * (double) qtreshold;

		for (int i = 1; i < count; i++) {
			int pCur = (seqstart + i) % pathlength;
			double t = cumDist[i] / totalChord;
			double b1 = (1.0 - t) * (1.0 - t);
			double b2 = 2.0 * (1.0 - t) * t;
			double b3 = t * t;
			double bx = (b1 * x1) + (b2 * cpx) + (b3 * x2);
			double by = (b1 * y1) + (b2 * cpy) + (b3 * y2);

			double px = path.get(pCur)[0], py = path.get(pCur)[1];
			double distSq = (px - bx) * (px - bx) + (py - by) * (py - by);

			if (distSq > qtresSq) curvePass = false;
			if (distSq > maxError) {
				maxError = distSq;
				errorpoint = pCur;
			}
		}

		// Return spline if fits
		if (curvePass) {
			Double[] seg = new Double[7];
			seg[0] = 2.0;
			seg[1] = x1; seg[2] = y1;
			seg[3] = cpx; seg[4] = cpy;
			seg[5] = x2; seg[6] = y2;
			segment.add(seg);
			return segment;
		}

		// 5.5. Spline failed: split at peak error point (Douglas-Peucker)
		int splitpoint = (errorpoint != seqstart && errorpoint != seqend) ? errorpoint : fitpoint;
		if (splitpoint == seqstart || splitpoint == seqend) {
			splitpoint = (seqstart + (count / 2)) % pathlength;
		}

		// 5.6. Recursively fit left and right halves
		segment = fitseq(path, ltreshold, qtreshold, seqstart, splitpoint);
		segment.addAll(fitseq(path, ltreshold, qtreshold, splitpoint, seqend));
		return segment;
	}


	// 5. Batch tracing paths
	public static ArrayList<ArrayList<Double[]>> batchtracepaths (ArrayList<ArrayList<Double[]>> internodepaths, float ltres,float qtres){
		ArrayList<ArrayList<Double[]>> btracedpaths = new ArrayList<ArrayList<Double[]>>();
		for(int k=0; k<internodepaths.size(); k++){
			btracedpaths.add(tracepath(internodepaths.get(k),ltres,qtres) );
		}
		return btracedpaths;
	}


	// 5. Batch tracing layers
	public static ArrayList<ArrayList<ArrayList<Double[]>>> batchtracelayers (ArrayList<ArrayList<ArrayList<Double[]>>> binternodes, float ltres, float qtres){
		ArrayList<ArrayList<ArrayList<Double[]>>> btbis = new ArrayList<ArrayList<ArrayList<Double[]>>>();
		for(int k=0; k<binternodes.size(); k++){
			btbis.add( batchtracepaths( binternodes.get(k),ltres,qtres) );
		}
		return btbis;
	}


	////////////////////////////////////////////////////////////
	//
	//  SVG Drawing functions
	//
	////////////////////////////////////////////////////////////

	public static float roundtodec (float val, float places){
		return (float)(Math.round(val*Math.pow(10,places))/Math.pow(10,places));
	}


	// Getting SVG path element string from a traced path
	public static void svgpathstring (StringBuilder sb, String desc, ArrayList<Double[]> segments, String colorstr, HashMap<String,Float> options){
		float scale = options.get("scale"), lcpr = options.get("lcpr"), qcpr = options.get("qcpr"), roundcoords = (float) Math.floor(options.get("roundcoords"));
		// Path
		sb.append("<path ").append(desc).append(colorstr).append("d=\"" ).append("M ").append(segments.get(0)[1]*scale).append(" ").append(segments.get(0)[2]*scale).append(" ");

		if( roundcoords == -1 ){
			for(int pcnt=0;pcnt<segments.size();pcnt++){
				if(segments.get(pcnt)[0]==1.0){
					sb.append("L ").append(segments.get(pcnt)[3]*scale).append(" ").append(segments.get(pcnt)[4]*scale).append(" ");
				}else{
					sb.append("Q ").append(segments.get(pcnt)[3]*scale).append(" ").append(segments.get(pcnt)[4]*scale).append(" ").append(segments.get(pcnt)[5]*scale).append(" ").append(segments.get(pcnt)[6]*scale).append(" ");
				}
			}
		}else{
			for(int pcnt=0;pcnt<segments.size();pcnt++){
				if(segments.get(pcnt)[0]==1.0){
					sb.append("L ").append(roundtodec((float)(segments.get(pcnt)[3]*scale),roundcoords)).append(" ")
					.append(roundtodec((float)(segments.get(pcnt)[4]*scale),roundcoords)).append(" ");
				}else{
					sb.append("Q ").append(roundtodec((float)(segments.get(pcnt)[3]*scale),roundcoords)).append(" ")
					.append(roundtodec((float)(segments.get(pcnt)[4]*scale),roundcoords)).append(" ")
					.append(roundtodec((float)(segments.get(pcnt)[5]*scale),roundcoords)).append(" ")
					.append(roundtodec((float)(segments.get(pcnt)[6]*scale),roundcoords)).append(" ");
				}
			}
		}// End of roundcoords check

		sb.append("Z\" />");

		// Rendering control points
		for(int pcnt=0;pcnt<segments.size();pcnt++){
			if((lcpr>0)&&(segments.get(pcnt)[0]==1.0)){
				sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(lcpr).append("\" fill=\"white\" stroke-width=\"").append(lcpr*0.2).append("\" stroke=\"black\" />");
			}
			if((qcpr>0)&&(segments.get(pcnt)[0]==2.0)){
				sb.append( "<circle cx=\"").append(segments.get(pcnt)[3]*scale).append("\" cy=\"").append(segments.get(pcnt)[4]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"cyan\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
				sb.append( "<circle cx=\"").append(segments.get(pcnt)[5]*scale).append("\" cy=\"").append(segments.get(pcnt)[6]*scale).append("\" r=\"").append(qcpr).append("\" fill=\"white\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"black\" />");
				sb.append( "<line x1=\"").append(segments.get(pcnt)[1]*scale).append("\" y1=\"").append(segments.get(pcnt)[2]*scale).append("\" x2=\"").append(segments.get(pcnt)[3]*scale).append("\" y2=\"").append(segments.get(pcnt)[4]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
				sb.append( "<line x1=\"").append(segments.get(pcnt)[3]*scale).append("\" y1=\"").append(segments.get(pcnt)[4]*scale).append("\" x2=\"").append(segments.get(pcnt)[5]*scale).append("\" y2=\"").append(segments.get(pcnt)[6]*scale).append("\" stroke-width=\"").append(qcpr*0.2).append("\" stroke=\"cyan\" />");
			}// End of quadratic control points
		}

	}// End of svgpathstring()


	// Converting tracedata to an SVG string, paths are drawn according to a Z-index
	// the optional lcpr and qcpr are linear and quadratic control point radiuses
	public static String getsvgstring (IndexedImage ii, HashMap<String,Float> options){
		return getsvgstring(ii, options, false, -1);
	}

	public static String getsvgstring (IndexedImage ii, HashMap<String,Float> options, boolean removeBackground, int bgLayerIdx){
		options = checkoptions(options);
		// SVG start
		int w = (int) (ii.width * options.get("scale")), h = (int) (ii.height * options.get("scale"));
		String viewboxorviewport = options.get("viewbox")!=0 ? "viewBox=\"0 0 "+w+" "+h+"\" " : "width=\""+w+"\" height=\""+h+"\" ";
		StringBuilder svgstr = new StringBuilder("<svg "+viewboxorviewport+"version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" ");
		if(options.get("desc")!=0){ svgstr.append("desc=\"Created with ImageTracer.java version "+ImageTracer.versionnumber+"\" "); }
		svgstr.append(">");

		// creating Z-index (using List to prevent silent overwriting of paths with identical coordinates)
		TreeMap <Double, ArrayList<Integer[]>> zindex = new TreeMap <Double, ArrayList<Integer[]>>();
		double label;
		// Layer loop
		for(int k=0; k<ii.layers.size(); k++) {

			// Path loop
			for(int pcnt=0; pcnt<ii.layers.get(k).size(); pcnt++){

				// Label (Z-index key) is the startpoint of the path, linearized
				label = (ii.layers.get(k).get(pcnt).get(0)[2] * w) + ii.layers.get(k).get(pcnt).get(0)[1];
				if(!zindex.containsKey(label)){ zindex.put(label, new ArrayList<Integer[]>()); }
				zindex.get(label).add(new Integer[]{Integer.valueOf(k), Integer.valueOf(pcnt)});
			}// End of path loop

		}// End of layer loop

		// Drawing
		// Z-index loop
		String thisdesc = "";
		for(Entry<Double, ArrayList<Integer[]>> entry : zindex.entrySet()) {
			for(Integer[] kp : entry.getValue()) {
				int layerIdx = kp[0];
				int pathIdx = kp[1];
				if(removeBackground && layerIdx == bgLayerIdx){
					continue; // Skip rendering background layer to produce transparent SVG
				}
				if(options.get("desc")!=0){ thisdesc = "desc=\"l "+layerIdx+" p "+pathIdx+"\" "; }else{ thisdesc = ""; }
				svgpathstring(svgstr,
						thisdesc,
						ii.layers.get(layerIdx).get(pathIdx),
						tosvgcolorstr(ii.palette[layerIdx], options),
						options);
			}
		}

		// SVG End
		svgstr.append("</svg>");

		return svgstr.toString();

	}// End of getsvgstring()


	static String tosvgcolorstr (byte[] c, HashMap<String,Float> options){
		float strokeWidth = 0.2f;
		if(options != null && options.containsKey("strokewidth")){
			strokeWidth = options.get("strokewidth");
		}
		if (strokeWidth <= 0.001f) {
			return "fill=\"rgb("+(c[0]+128)+","+(c[1]+128)+","+(c[2]+128)+")\" stroke=\"none\" opacity=\""+((c[3]+128)/255.0)+"\" ";
		}
		return "fill=\"rgb("+(c[0]+128)+","+(c[1]+128)+","+(c[2]+128)+")\" stroke=\"rgb("+(c[0]+128)+","+(c[1]+128)+","+(c[2]+128)+")\" stroke-width=\""+strokeWidth+"\" stroke-linejoin=\"round\" opacity=\""+((c[3]+128)/255.0)+"\" ";
	}

	static String tosvgcolorstr (byte[] c){
		return tosvgcolorstr(c, null);
	}


	// Gaussian kernels for blur
	static double[][] gks = { {0.27901,0.44198,0.27901}, {0.135336,0.228569,0.272192,0.228569,0.135336}, {0.086776,0.136394,0.178908,0.195843,0.178908,0.136394,0.086776},
			{0.063327,0.093095,0.122589,0.144599,0.152781,0.144599,0.122589,0.093095,0.063327}, {0.049692,0.069304,0.089767,0.107988,0.120651,0.125194,0.120651,0.107988,0.089767,0.069304,0.049692} };


	// Selective Gaussian blur for preprocessing
	static ImageData blur (ImageData imgd, float rad, float del){
		int i,j,k,d,idx;
		double racc,gacc,bacc,aacc,wacc;
		ImageData imgd2 = new ImageData(imgd.width,imgd.height,new byte[imgd.width*imgd.height*4]);

		// radius and delta limits, this kernel
		int radius = (int)Math.floor(rad); if(radius<1){ return imgd; } if(radius>5){ radius = 5; }
		int delta = (int)Math.abs(del); if(delta>1024){ delta = 1024; }
		double[] thisgk = gks[radius-1];

		// loop through all pixels, horizontal blur
		for( j=0; j < imgd.height; j++ ){
			for( i=0; i < imgd.width; i++ ){

				racc = 0; gacc = 0; bacc = 0; aacc = 0; wacc = 0;
				// gauss kernel loop
				for( k = -radius; k < (radius+1); k++){
					// add weighted color values
					if( ((i+k) > 0) && ((i+k) < imgd.width) ){
						idx = ((j*imgd.width)+i+k)*4;
						racc += imgd.data[idx  ] * thisgk[k+radius];
						gacc += imgd.data[idx+1] * thisgk[k+radius];
						bacc += imgd.data[idx+2] * thisgk[k+radius];
						aacc += imgd.data[idx+3] * thisgk[k+radius];
						wacc += thisgk[k+radius];
					}
				}
				// The new pixel
				idx = ((j*imgd.width)+i)*4;
				imgd2.data[idx  ] = (byte) Math.floor(racc / wacc);
				imgd2.data[idx+1] = (byte) Math.floor(gacc / wacc);
				imgd2.data[idx+2] = (byte) Math.floor(bacc / wacc);
				imgd2.data[idx+3] = (byte) Math.floor(aacc / wacc);

			}// End of width loop
		}// End of horizontal blur

		// copying the half blurred imgd2
		byte[] himgd = imgd2.data.clone();

		// loop through all pixels, vertical blur
		for( j=0; j < imgd.height; j++ ){
			for( i=0; i < imgd.width; i++ ){

				racc = 0; gacc = 0; bacc = 0; aacc = 0; wacc = 0;
				// gauss kernel loop
				for( k = -radius; k < (radius+1); k++){
					// add weighted color values
					if( ((j+k) > 0) && ((j+k) < imgd.height) ){
						idx = (((j+k)*imgd.width)+i)*4;
						racc += himgd[idx  ] * thisgk[k+radius];
						gacc += himgd[idx+1] * thisgk[k+radius];
						bacc += himgd[idx+2] * thisgk[k+radius];
						aacc += himgd[idx+3] * thisgk[k+radius];
						wacc += thisgk[k+radius];
					}
				}
				// The new pixel
				idx = ((j*imgd.width)+i)*4;
				imgd2.data[idx  ] = (byte) Math.floor(racc / wacc);
				imgd2.data[idx+1] = (byte) Math.floor(gacc / wacc);
				imgd2.data[idx+2] = (byte) Math.floor(bacc / wacc);
				imgd2.data[idx+3] = (byte) Math.floor(aacc / wacc);

			}// End of width loop
		}// End of vertical blur

		// Selective blur: loop through all pixels
		for( j=0; j < imgd.height; j++ ){
			for( i=0; i < imgd.width; i++ ){

				idx = ((j*imgd.width)+i)*4;
				// d is the difference between the blurred and the original pixel
				d = Math.abs(imgd2.data[idx  ] - imgd.data[idx  ]) + Math.abs(imgd2.data[idx+1] - imgd.data[idx+1]) +
						Math.abs(imgd2.data[idx+2] - imgd.data[idx+2]) + Math.abs(imgd2.data[idx+3] - imgd.data[idx+3]);
				// selective blur: if d>delta, put the original pixel back
				if(d>delta){
					imgd2.data[idx  ] = imgd.data[idx  ];
					imgd2.data[idx+1] = imgd.data[idx+1];
					imgd2.data[idx+2] = imgd.data[idx+2];
					imgd2.data[idx+3] = imgd.data[idx+3];
				}
			}
		}// End of Selective blur

		return imgd2;

	}// End of blur()


}// End of ImageTracer class