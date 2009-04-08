package play.test;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import play.Invoker.Invocation;
import play.Play;
import play.Logger;

/**
 * Run application tests
 * ??
 */
public class TestEngine {

    public static void main(String[] args) {

        File root = new File(System.getProperty("application.path"));
        Play.init(root, "test");
        Play.start();

        JUnitCore junit = new JUnitCore();
        junit.addListener(new Listener());
        boolean allOk = true;

        List<Class> testClasses = Play.classloader.getAllClasses();

        if (testClasses.isEmpty()) {
            Logger.info("");
            Logger.info("No test to run");
        } else {
            for (Class testClass : testClasses) {
                if (VirtualClientTest.class.isAssignableFrom(testClass)) {
                    Logger.info("");
                    Logger.info("Running %s ...", testClass.getSimpleName());
                    Result result = junit.run(testClass);
                    if (result.wasSuccessful()) {
                        Logger.info("OK", result.getRunCount());
                    } else {
                        Logger.error("FAILED. %s test%s failed", result.getFailureCount(), result.getFailureCount() > 1 ? "s have" : " has");
                    }
                    Logger.info("");
                    allOk = allOk && result.wasSuccessful();
                }
            }
            if (allOk) {
                Logger.info("All tests are OK");
            } else {
                Logger.error("Tests have failed");
            }
        }

        Play.stop();
        System.exit(0);

    }

    static ExecutorService executor = Executors.newCachedThreadPool();

    public static List<Class> allSimpleTests() {
        return Play.classloader.getAssignableClasses(SimpleTest.class);
    }

    public static List<Class> allVirtualClientTests() {
        return Play.classloader.getAssignableClasses(VirtualClientTest.class);
    }

    public static boolean run(final String name) {
        // Result
        Result result = null;

        try {
            // Load test class
            final Class testClass = Play.classloader.loadClass(name);

            // Simple test
            if(SimpleTest.class.isAssignableFrom(testClass)) {
                JUnitCore junit = new JUnitCore();
                junit.addListener(new Listener());
                result = junit.run(testClass);
            }

            // VirtualClient test
            if(VirtualClientTest.class.isAssignableFrom(testClass)) {
                Future<Result> futureResult = executor.submit(new Callable<Result>() {
                    public Result call() throws Exception {
                        JUnitCore junit = new JUnitCore();
                        junit.addListener(new Listener());
                        return junit.run(testClass);
                    }
                });
                try {
                    result = futureResult.get();
                } catch(Exception e) {
                    Logger.error("VirtualClient test has failed", e);
                }
            }
        } catch(ClassNotFoundException e) {
            Logger.error(e, "Test not found %s", name);
        }

        return result != null && result.wasSuccessful();
    }

    // ~~~~~~ Run listener
    static class Listener extends RunListener {

        @Override
        public void testStarted(Description description) throws Exception {
            Logger.info("    - %s", description.getDisplayName());
            lastTestHasFailed = false;
        }

        @Override
        public void testFailure(Failure failure) throws Exception {
            Logger.info("    ! %s", failure.getMessage() == null ? "Oops" : failure.getMessage());
            if (!(failure.getException() instanceof AssertionError)) {
                Logger.error(failure.getException(), "    ! Exception raised is");
                Invocation.onException(failure.getException());
                Invocation._finally();
            }
            lastTestHasFailed = true;
        }

        @Override
        public void testFinished(Description arg0) throws Exception {
            if (lastTestHasFailed) {
                Logger.info("");
            }
        }
        boolean lastTestHasFailed = false;
    }
}
