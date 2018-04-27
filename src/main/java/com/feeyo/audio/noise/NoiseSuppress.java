package com.feeyo.audio.noise;

import com.feeyo.util.VolumeUtil;

public class NoiseSuppress {
	
	private NoiseReduction inHandle;
	
	public NoiseSuppress(int inSampleRate, int inFrameSize) {
		inHandle = createNoiseReduction(inSampleRate, inFrameSize);
	}
	
	public byte[] noiseReductionProcess(byte[] pcm) {
		
		int i;
		inHandle.adapt_count++;
		short[] ioPcm = VolumeUtil.byteArray2shortArray(pcm);
		frequencyAnalysis(ioPcm);
		noiseEstimation();
		audioEnhancement();

		FftAlgorithm.spx_ifft(inHandle.fft_table, inHandle.ft, inHandle.outpcm);
		// overlap and add
		for (i = 0; i < inHandle.frame_size; i++) {
			ioPcm[i] = (short) (inHandle.outbuf[i] + inHandle.outpcm[i]);
		}
		for (i = 0; i < inHandle.frame_size; i++)
			inHandle.outbuf[i] = inHandle.outpcm[inHandle.frame_size + i] * inHandle.win_gain;

		// limit value of adapt_count
		if (inHandle.adapt_count > 16000)
			inHandle.adapt_count = 2;
		return VolumeUtil.shortArray2byteArray(ioPcm);
	}

	private void audioEnhancement() {
		
		int i;
		inHandle.ft[0] = 0; // ignore DC component

		for (i = 1; i < inHandle.ps_size; i++) {
			float gain;
			float offset;
			float priorSNR, postSNR;
			float alpha = 0;
			// compute priori SNR and post SNR
			postSNR = inHandle.sold[i] / inHandle.noise[i];
			priorSNR = postSNR - 1;
			offset = (priorSNR - inHandle.prior[i]) / postSNR;
			alpha = 1 / (1 + offset * offset);

			inHandle.prior[i] = (float) (alpha * (4.0f / Math.PI) * inHandle.prior[i] + (1 - alpha) * Math.max(priorSNR, 0));
			// compute weiner gain
			gain = inHandle.prior[i] / (inHandle.prior[i] + 1);
			
			inHandle.prior[i] = priorSNR;
			inHandle.post[i] = postSNR;

			// apply gain
			inHandle.ft[2 * i - 1] *= gain;
			inHandle.ft[2 * i] *= gain;

		}
		
	}

	private void noiseEstimation() {
		
		float beta = 0.2f; // look-ahead factor,control adaptive time
		float gamma = 0.998f;
		int i;

		if (1 == inHandle.adapt_count) {

			// assume the first frame is noise
			for (i = 0; i < inHandle.ps_size; i++) {
				inHandle.sold[i] = inHandle.ps[i];
				inHandle.smin[i] = inHandle.ps[i];
				inHandle.noise[i] = inHandle.ps[i];
				inHandle.post[i] = 1.0F;
				inHandle.prior[i] = 0;
			}
		}

		for (i = 0; i < inHandle.ps_size; i++) {
			float Sp;
			// smooth power spectrum
			Sp = 0.2f * inHandle.sold[i] + 0.8f * inHandle.ps[i];
			// update noise
			if (inHandle.sold[i] <= Sp)
				inHandle.noise[i] = gamma * inHandle.noise[i] + (1.0f - gamma) / (1.0f - beta) * (Sp - beta * inHandle.sold[i]);
			else
				inHandle.noise[i] = Sp;

			inHandle.sold[i] = Sp;
		}

	}

	private void frequencyAnalysis(short[] x) {

		int i;
		removeDC(x, inHandle.ps_size);

		for (i = 0; i < inHandle.ps_size; i++)
			inHandle.frame[i] = inHandle.inbuf[i];
		for (i = 0; i < inHandle.ps_size; i++)
			inHandle.frame[i + inHandle.ps_size] = x[i];
		for (i = 0; i < inHandle.ps_size; i++)
			inHandle.inbuf[i] = x[i];

		for (i = 0; i < 2 * inHandle.ps_size; i++)
			inHandle.frame[i] *= inHandle.window[i];

		FftAlgorithm.spx_fft(inHandle.fft_table, inHandle.frame, inHandle.ft);

		inHandle.ps[0] = inHandle.ft[0] * inHandle.ft[0];
		for (i = 1; i < inHandle.ps_size; i++)
			inHandle.ps[i] = inHandle.ft[2 * i - 1] * inHandle.ft[2 * i - 1] + inHandle.ft[2 * i] * inHandle.ft[2 * i];

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

		VolumeUtil.hamming(2 * N, pInst.window);
		total_win_gain = 0;
		for (i = 0; i < 2 * N; i++)
			total_win_gain += pInst.window[i];
		pInst.win_gain = N / total_win_gain;
		pInst.adapt_count = 0;
		return pInst;
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

