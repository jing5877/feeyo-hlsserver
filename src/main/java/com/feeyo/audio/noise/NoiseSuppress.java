package com.feeyo.audio.noise;

public class NoiseSuppress {
	
	//
	private NoiseReduction noiseReduction;
	
	public NoiseSuppress(int sampleRate, int frameSize) {
		noiseReduction = createNoiseReduction(sampleRate, frameSize);
	}
	
	public short[] noiseReductionProcess(short[] pcm) {
		
		int i;
		noiseReduction.adapt_count++;

		frequencyAnalysis(pcm);
		noiseEstimation();
		audioEnhancement();

		FftAlgorithm.spx_ifft(noiseReduction.fft_table, noiseReduction.ft, noiseReduction.outpcm);
		// overlap and add
		for (i = 0; i < noiseReduction.frame_size; i++) {
			pcm[i] = (short) (noiseReduction.outbuf[i] + noiseReduction.outpcm[i]);
		}
		for (i = 0; i < noiseReduction.frame_size; i++)
			noiseReduction.outbuf[i] = noiseReduction.outpcm[noiseReduction.frame_size + i] * noiseReduction.win_gain;

		// limit value of adapt_count
		if (noiseReduction.adapt_count > 16000)
			noiseReduction.adapt_count = 2;
		
		return pcm;
	}

	private void audioEnhancement() {
		
		int i;
		noiseReduction.ft[0] = 0; // ignore DC component

		for (i = 1; i < noiseReduction.ps_size; i++) {
			float gain;
			float offset;
			float priorSNR, postSNR;
			float alpha = 0;
			// compute priori SNR and post SNR
			postSNR = noiseReduction.sold[i] / noiseReduction.noise[i];
			priorSNR = postSNR - 1;
			offset = (priorSNR - noiseReduction.prior[i]) / postSNR;
			alpha = 1 / (1 + offset * offset);

			noiseReduction.prior[i] = (float) (alpha * (4.0f / Math.PI) * noiseReduction.prior[i] + (1 - alpha) * Math.max(priorSNR, 0));
			// compute weiner gain
			gain = noiseReduction.prior[i] / (noiseReduction.prior[i] + 1);
			
			noiseReduction.prior[i] = priorSNR;
			noiseReduction.post[i] = postSNR;

			// apply gain
			noiseReduction.ft[2 * i - 1] *= gain;
			noiseReduction.ft[2 * i] *= gain;

		}
		
	}

	private void noiseEstimation() {
		
		float beta = 0.2f; // look-ahead factor,control adaptive time
		float gamma = 0.998f;
		int i;

		if (1 == noiseReduction.adapt_count) {

			// assume the first frame is noise
			for (i = 0; i < noiseReduction.ps_size; i++) {
				noiseReduction.sold[i] = noiseReduction.ps[i];
				noiseReduction.smin[i] = noiseReduction.ps[i];
				noiseReduction.noise[i] = noiseReduction.ps[i];
				noiseReduction.post[i] = 1.0F;
				noiseReduction.prior[i] = 0;
			}
		}

		for (i = 0; i < noiseReduction.ps_size; i++) {
			float Sp;
			// smooth power spectrum
			Sp = 0.2f * noiseReduction.sold[i] + 0.8f * noiseReduction.ps[i];
			// update noise
			if (noiseReduction.sold[i] <= Sp)
				noiseReduction.noise[i] = gamma * noiseReduction.noise[i] + (1.0f - gamma) / (1.0f - beta) * (Sp - beta * noiseReduction.sold[i]);
			else
				noiseReduction.noise[i] = Sp;

			noiseReduction.sold[i] = Sp;
		}

	}

	private void frequencyAnalysis(short[] x) {

		int i;
		removeDC(x, noiseReduction.ps_size);

		for (i = 0; i < noiseReduction.ps_size; i++)
			noiseReduction.frame[i] = noiseReduction.inbuf[i];
		for (i = 0; i < noiseReduction.ps_size; i++)
			noiseReduction.frame[i + noiseReduction.ps_size] = x[i];
		for (i = 0; i < noiseReduction.ps_size; i++)
			noiseReduction.inbuf[i] = x[i];

		for (i = 0; i < 2 * noiseReduction.ps_size; i++)
			noiseReduction.frame[i] *= noiseReduction.window[i];

		FftAlgorithm.spx_fft(noiseReduction.fft_table, noiseReduction.frame, noiseReduction.ft);

		noiseReduction.ps[0] = noiseReduction.ft[0] * noiseReduction.ft[0];
		for (i = 1; i < noiseReduction.ps_size; i++)
			noiseReduction.ps[i] = noiseReduction.ft[2 * i - 1] * noiseReduction.ft[2 * i - 1] + noiseReduction.ft[2 * i] * noiseReduction.ft[2 * i];

	}

	private void removeDC(short[] x, int inLen) {
		int i, nOffset;
		float fSum = 0.0f;

		for (i = 0; i < inLen; i++) {
			fSum += x[i];
		}
		nOffset = (int) (-fSum / inLen);
		for (i = 0; i < inLen; i++) {
			x[i] += nOffset;

			if (x[i] > 32000)
				x[i] = 32000;
			else if (x[i] < -32000)
				x[i] = -32000;
		}
	}

	private NoiseReduction createNoiseReduction(int inSampleRate, int inFrameSize) {
		int i, N;
		float total_win_gain;

		NoiseReduction pInst = new NoiseReduction();
		pInst.sample_rate = inSampleRate;
		pInst.frame_size = inFrameSize;
		pInst.ps_size = inFrameSize;

		N = pInst.frame_size;

		pInst.frame = new float[2 * N];
		pInst.window = new float[2 * N];
		pInst.ft = new float[2 * N];
		pInst.ps = new float[N];
		pInst.sold = new float[N];
		pInst.smin = new float[N];
		pInst.noise = new float[N];
		pInst.prior = new float[N];
		pInst.post = new float[N];
		pInst.prob = new float[N];
		pInst.inbuf = new float[N];
		pInst.outbuf = new float[N];
		for (i = 0; i < N; i++) {
			pInst.inbuf[i] = 0;
			pInst.outbuf[i] = 0;
			pInst.prob[i] = 0;
		}

		pInst.fft_table = FftAlgorithm.spx_fft_init(2 * N);
		pInst.outpcm = new float[2 * N];

		hamming(2 * N, pInst.window);
		total_win_gain = 0;
		for (i = 0; i < 2 * N; i++)
			total_win_gain += pInst.window[i];
		pInst.win_gain = N / total_win_gain;
		pInst.adapt_count = 0;
		return pInst;
	}
	
	
	/*
	 * Hamming 2*pi*k w(k) = 0.54 - 0.46*cos(------), where 0 <= k < N N-1
	 *
	 * n window length w buffer for the window parameters
	 */
	private void hamming(int n, float[] w) {
		int i;
		float k = (float) (2 * Math.PI / (n - 1)); /* 2*pi/(N-1) */

		for (i = 0; i < n; i++)
			w[i] = (float) (0.54 - 0.46 * Math.cos(k * i));
	}
	
	
	class NoiseReduction {

		public int sample_rate;
		public int frame_size;
		public int ps_size; // size of power spectrum

		public float[] frame;
		public float[] window; // window function
		public float[] ft; // FFT coefficient
		public float[] ps; // power spectrum
		public float[] sold; // smoothed power spectrum
		public float[] smin; // minimum power spectrum
		public float[] noise; // noise power spectrum
		public float[] prior; // priori SNR
		public float[] post; // posteriori SNR
		public float[] prob; // speech/noise probability.

		public float[] inbuf; // overlapped add analysis
		public float[] outbuf;
		public float win_gain; // gain by window function
		public DrftLookup fft_table;
		public float[] outpcm;
		public int adapt_count;
	}

	static class DrftLookup {
		public int n;
		public float[] trigcache;
		public int[] splitcache;
	};

}

