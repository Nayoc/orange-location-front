package com.example.indoorlocation.util;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    /**
     * 将Uri转换为File对象
     * @param context 上下文
     * @param uri 图片Uri
     * @return 转换后的File对象，失败返回null
     */
    public static File uriToFile(Context context, Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        File tempFile = null;

        try {
            // 检查Uri和上下文是否有效
            if (uri == null || context == null) {
                return null;
            }

            // 获取输入流
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                return null;
            }

            // 创建临时文件
            String fileName = "space_plan_" + System.currentTimeMillis() + ".jpg";
            File cacheDir = context.getExternalCacheDir();

            // 如果外部缓存目录不可用，使用内部缓存目录
            if (cacheDir == null) {
                cacheDir = context.getCacheDir();
            }

            tempFile = new File(cacheDir, fileName);

            // 写入文件
            outputStream = new FileOutputStream(tempFile);
            byte[] buffer = new byte[1024 * 4]; // 4KB缓冲区
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();

            return tempFile;

        } catch (IOException e) {
            e.printStackTrace();
            // 发生异常时删除可能创建的不完整文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        } finally {
            // 确保流被关闭
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
