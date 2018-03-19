package com.feeyo.oss;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.LifecycleRule;
import com.aliyun.oss.model.LifecycleRule.StorageTransition;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.SetBucketLifecycleRequest;

public class OSSOperation {

	private final Logger LOGGER = LoggerFactory.getLogger(OSSOperation.class);

	private OSSClient ossClient;

	public OSSOperation() {
		ossClient = new OSSClient(OSSOperationConfig.endpoint, OSSOperationConfig.accessKeyId,
				OSSOperationConfig.accessKeySecret);
	}

	/**
	 * 本地文件上传至oss，并删除本地文件
	 */
	public boolean uploadObject(String filePath, String fileName, long streamId) {
		boolean result = false;
		File file = new File(filePath + File.separator + fileName);
		if (file.exists()) {
			// 文件上传至阿里云OSS
			ossClient.putObject(OSSOperationConfig.bucketName, String.valueOf(streamId) + "/" + fileName, file);
			// 删除本地文件
			file.delete();
			result = true;
		}
		return result;
	}

	/**
	 * 上传byte数组
	 */
	public void uploadObject(byte[] mp3, String fileName, long streamId) {
		// 文件上传至阿里云OSS
		ossClient.putObject(OSSOperationConfig.bucketName, String.valueOf(streamId) + "/" + fileName, new ByteArrayInputStream(mp3));
	}

	/**
	 * 获取OSS Object输入流
	 */
	public InputStream readObject(String fileName, long streamId) {
		OSSObject ossObject = ossClient.getObject(OSSOperationConfig.bucketName, String.valueOf(streamId) + "/" + fileName);
		InputStream inputStream = ossObject.getObjectContent();
		return inputStream;
	}

	/**
	 * 删除文件
	 */
	public void deleteObject(String fileName, long streamId) {
		ossClient.deleteObject(OSSOperationConfig.bucketName, String.valueOf(streamId) + "/" + fileName);
	}

	/**
	 * 判断object在OSS上是否存在
	 */
	public boolean doesObjectExist(String fileName, long streamId) {
		return ossClient.doesObjectExist(OSSOperationConfig.bucketName, String.valueOf(streamId) + "/" + fileName);
	}

	/**
	 * 获取object的大小
	 */
	public long getObjectLength(String fileName, long streamId) {
		ObjectMetadata objectMetadata = ossClient.getObjectMetadata(OSSOperationConfig.bucketName,
				String.valueOf(streamId) + "/" + fileName);
		return objectMetadata.getContentLength();
	}

	/**
	 * 获取object头信息
	 */
	public ObjectMetadata getObjectMetadata(String fileName, long streamId) {
		ObjectMetadata objectMetadata = ossClient.getObjectMetadata(OSSOperationConfig.bucketName,
				String.valueOf(streamId) + "/" + fileName);
		return objectMetadata;
	}

	/**
	 * 更新生命周期
	 */
	public void updateExpirationDays(int days) {
		// 获取生命周期规则列表
		List<LifecycleRule> list = ossClient.getBucketLifecycle(OSSOperationConfig.bucketName);
		if (list.size() > 0) {
			LifecycleRule lifecycleRule = list.get(0);
			List<StorageTransition> storageTransitionList = lifecycleRule.getStorageTransition();
			if (storageTransitionList.size() > 0) {
				StorageTransition storageTransition = storageTransitionList.get(0);

				// 如果原转换到归档存储的时间 与设定时间不一样，则更新
				if (storageTransition.getExpirationDays() != days) {
					storageTransition.setExpirationDays(days);

					SetBucketLifecycleRequest setBucketLifecycleRequest = new SetBucketLifecycleRequest(
							OSSOperationConfig.bucketName);
					setBucketLifecycleRequest.setLifecycleRules(list);
					ossClient.setBucketLifecycle(setBucketLifecycleRequest);
					LOGGER.info("oss life cycle set expir : " + days + " days");
				}
			}
		}
	}


	public void closeOSSClient() {
		if (ossClient != null) {
			ossClient.shutdown();
		}
	}
}
