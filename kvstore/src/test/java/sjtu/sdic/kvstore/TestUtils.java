package sjtu.sdic.kvstore;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TestUtils {
    public static Field getField(String className, String fieldName) {
        try {
            Class<?> aClass = Class.forName(className);
            Field declaredField = aClass.getDeclaredField(fieldName);
            //   if not public,you should call this
            declaredField.setAccessible(true);
            return declaredField;
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Method getMethod(String className, String methodName, Class<?>... clzs) {
        try {
            Class<?> aClass = Class.forName(className);
            Method declaredMethod = aClass.getDeclaredMethod(methodName, clzs);
            declaredMethod.setAccessible(true);
            return declaredMethod;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void doReader(int i) {
        try {
            System.out.println("Reader" + i + " do job - step 1");
            Thread.sleep(300);
            System.out.println("Reader" + i + " do job - step 2");
            Thread.sleep(200);
            System.out.println("Reader" + i + " do job - completed");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public static void doWriter(int i) {
        try {
            System.out.println("Writer" + i + " do job - step 1");
            Thread.sleep(300);
            System.out.println("Writer" + i + " do job - step 2");
            Thread.sleep(200);
            System.out.println("Writer" + i + " do job - completed");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
