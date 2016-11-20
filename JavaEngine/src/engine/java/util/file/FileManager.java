package engine.java.util.file;

import engine.java.util.io.IOUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * 文件管理器<br>
 * 功能：文件操作
 * 
 * @author Daimon
 * @version N
 * @since 9/26/2012
 */

public final class FileManager {

    /**
     * 复制文件
     */

    public static boolean copyFile(File desFile, File srcFile) {
        if (!srcFile.exists())
        {
            throw new FileException(String.format("%s is not exist.", srcFile));
        }

        if (srcFile.isDirectory())
        {
            throw new FileException(String.format("%s is directory.", srcFile));
        }

        createFileIfNecessary(desFile);
        return FileUtils.copyFile(srcFile, desFile);
    }

    /**
     * 批量复制文件（夹）到指定目录
     * 
     * @param desDir 指定目录
     * @param files 欲复制的文件（夹）列表
     */

    public static boolean copyTo(File desDir, File... files) {
        if (files == null || files.length == 0)
        {
            throw new FileException("请指定需要复制的文件（夹）");
        }
        
        for (File file : files)
        {
            if (!copyTo(desDir, file))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * 复制单个文件（夹）到指定目录
     * 
     * @param desDir 指定目录
     * @param srcFile 欲复制的文件（夹）
     */

    public static boolean copyTo(File desDir, File srcFile) {
        if (!srcFile.exists())
        {
            throw new FileException(String.format("%s is not exist.", srcFile));
        }

        if (srcFile.isDirectory())
        {
            for (File f : srcFile.listFiles())
            {
                if (!copyTo(new File(desDir, srcFile.getName()), f))
                {
                    return false;
                }
            }
        }
        else
        {
            File file = new File(desDir, srcFile.getName());
            return copyFile(file, srcFile);
        }

        return true;
    }

    /**
     * 删除文件或目录（即使包含有文件）
     */

    public static void delete(File file) {
        if (!file.exists())
        {
            return;
        }

        if (file.isDirectory())
        {
            for (File f : file.listFiles())
            {
                delete(f);
            }

            file.delete();
        }
        else
        {
            file.delete();
        }
    }

    /**
     * 清空目录
     */

    public static void clearDir(File file) {
        if (!file.exists())
        {
            return;
        }

        if (file.isDirectory())
        {
            for (File f : file.listFiles())
            {
                delete(f);
            }
        }
    }

    /**
     * 创建文件以便写入
     */

    public static void createFileIfNecessary(File file) {
        if (file.isDirectory())
        {
            throw new FileException(String.format("%s is directory.", file));
        }

        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs())
        {
            throw new FileException(String.format("Cannot create parent dir[%s]", parent));
        }

        if (!file.exists())
        {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new FileException(String.format("Cannot create file[%s]", file), e);
            }
        }
    }

    /**
     * 获取文件（夹）大小
     */

    public static long getFileSize(File file) {
        if (file.isDirectory())
        {
            long size = 0;
            for (File f : file.listFiles())
            {
                size += getFileSize(f);
            }

            return size;
        }
        else
        {
            return file.length();
        }
    }

    /**
     * 根据文件名搜索文件
     * 
     * @param dir 搜索目录
     * @param fileName 搜索文件名
     * @return 如没搜到则返回Null
     */

    public static File searchFile(File dir, String fileName) {
        if (!(dir.exists() && dir.isDirectory()))
        {
            throw new FileException(new IllegalArgumentException());
        }

        File[] files = dir.listFiles();
        for (File file : files)
        {
            if (file.isDirectory())
            {
                file = searchFile(file, fileName);
                if (file != null)
                {
                    return file;
                }
            }
            else if (file.getName().equals(fileName))
            {
                return file;
            }
        }

        return null;
    }

    /**
     * 将文件内容映射到内存中（慎用）
     */

    public static MappedByteBuffer mapToBuffer(File file) throws Exception {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            MappedByteBuffer mbb = raf.getChannel().map(MapMode.READ_ONLY, 0, raf.length());
            mbb.order(ByteOrder.nativeOrder());
            return mbb;
        } finally {
            if (raf != null)
            {
                raf.close();
            }
        }
    }

    /**
     * 读取文件内容
     */

    public static byte[] readFile(File file) {
        try {
            FileInputStream fis = null;
            try {
                return IOUtil.readStream(fis = new FileInputStream(file));
            } finally {
                if (fis != null)
                {
                    fis.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 写入内容到文件
     * 
     * @param file 文件
     * @param content 写入内容
     * @param append true为追加,false为覆盖
     * @return 是否成功写入
     */

    public static boolean writeFile(File file, byte[] content, boolean append) {
        createFileIfNecessary(file);
        try {
            FileOutputStream fos = null;
            try {
                // The file will be created if it does not exist.
                fos = new FileOutputStream(file, append);
                fos.write(content);
                return true;
            } finally {
                if (fos != null)
                {
                    fos.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 逐行读取文件内容
     */

    public static String[] readLines(File file) {
        List<String> list = null;
        try {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(file));
                list = new LinkedList<String>();
                String s;
                while ((s = br.readLine()) != null)
                {
                    list.add(s);
                }
            } finally {
                if (br != null)
                {
                    br.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return list == null ? null : list.toArray(new String[list.size()]);
    }

    /**
     * 读取第一行文件内容
     */

    public static String readFirstLine(File file) {
        String s = null;
        try {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(file));
                s = br.readLine();
            } finally {
                if (br != null)
                {
                    br.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return s;
    }

    /**
     * 从文件导入数据
     * 
     * @param input 指定读入文件
     * @param c 数据集合
     * @return 操作是否成功
     */

    public static boolean importData(File input, Collection<Object> c) {
        if (c == null || input == null)
        {
            throw new FileException(new NullPointerException());
        }

        try {
            ObjectInputStream ois = null;
            try {
                ois = new ObjectInputStream(new FileInputStream(input));
                if (ois.readInt() == 0x43902756)
                {
                    Object obj = null;
                    while ((obj = ois.readObject()) != null)
                    {
                        c.add(obj);
                    }

                    return true;
                }
            } finally {
                if (ois != null)
                {
                    ois.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 导出数据到文件
     * 
     * @param c 数据集合
     * @param output 指定输出文件
     * @return 操作是否成功
     */

    public static boolean exportData(Collection<Object> c, File output) {
        if (c == null || output == null)
        {
            throw new FileException(new NullPointerException());
        }

        if (output.exists())
        {
            try {
                ObjectInputStream ois = null;
                try {
                    ois = new ObjectInputStream(new FileInputStream(output));
                    if (ois.readInt() != 0x43902756)
                    {
                        return false;
                    }
                } finally {
                    if (ois != null)
                    {
                        ois.close();
                    }
                }
            } catch (FileNotFoundException e) {
                // Ignore.
            } catch (IOException e) {
                return false;
            }
        }

        try {
            ObjectOutputStream oos = null;
            try {
                oos = new ObjectOutputStream(new FileOutputStream(output));
                oos.writeInt(0x43902756);
                for (Object obj : c)
                {
                    if (obj != null)
                    {
                        oos.writeObject(obj);
                    }
                }

                return true;
            } finally {
                if (oos != null)
                {
                    oos.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private static class FileException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public FileException(String detailMessage) {
            super(detailMessage);
        }

        public FileException(Throwable cause) {
            super(cause);
        }

        public FileException(String detailMessage, Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}