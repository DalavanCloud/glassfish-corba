/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2003-2007 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.corba.se.impl.orbutil.threadpool;

import java.io.IOException ;
import java.io.Closeable ;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.util.List ;
import java.util.ArrayList ;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.sun.corba.se.spi.monitoring.LongMonitoredAttributeBase;
import com.sun.corba.se.spi.monitoring.MonitoringConstants;
import com.sun.corba.se.spi.monitoring.MonitoredObject;
import com.sun.corba.se.spi.monitoring.MonitoringFactories;
import com.sun.corba.se.spi.orb.ORB;
import com.sun.corba.se.spi.orbutil.threadpool.NoSuchWorkQueueException;
import com.sun.corba.se.spi.orbutil.threadpool.ThreadPool;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import com.sun.corba.se.spi.orbutil.threadpool.WorkQueue;

import com.sun.corba.se.impl.logging.ORBUtilSystemException ;
import com.sun.corba.se.spi.orbutil.ORBConstants;


public class ThreadPoolImpl implements ThreadPool
{
    // serial counter useful for debugging
    private static AtomicInteger threadCounter = new AtomicInteger(0);
    private static final ORBUtilSystemException wrapper = 
	ORB.getStaticLogWrapperTable().get_RPC_TRANSPORT_ORBUtil() ;

    // Any time currentThreadCount and/or availableWorkerThreads is updated
    // or accessed this ThreadPool's WorkQueue must be locked. And, it is 
    // expected that this ThreadPool's WorkQueue is the only object that
    // updates and accesses these values directly and indirectly though a
    // call to a method in this ThreadPool. If any call to update or access
    // those values must synchronized on this ThreadPool's WorkQueue.
    final private WorkQueue workQueue;
    
    // Stores the number of available worker threads
    private int availableWorkerThreads = 0;
    
    // Stores the number of threads in the threadpool currently
    private int currentThreadCount = 0;
    
    // Minimum number of worker threads created at instantiation of the threadpool
    final private int minWorkerThreads;
    
    // Maximum number of worker threads in the threadpool
    final private int maxWorkerThreads;
    
    // Inactivity timeout value for worker threads to exit and stop running
    final private long inactivityTimeout;
    
    // Running count of the work items processed
    // Set the value to 1 so that divide by zero is avoided in 
    // averageWorkCompletionTime()
    private AtomicLong processedCount = new AtomicLong(1);
    
    // Running aggregate of the time taken in millis to execute work items
    // processed by the threads in the threadpool
    private AtomicLong totalTimeTaken = new AtomicLong(0);

    // Name of the ThreadPool
    final private String name;

    // MonitoredObject for ThreadPool
    private MonitoredObject threadpoolMonitoredObject;
    
    // ThreadGroup in which threads should be created
    private ThreadGroup threadGroup ;

    final private ClassLoader workerThreadClassLoader ; 

    Object workersLock = new Object() ;
    List<WorkerThread> workers = new ArrayList<WorkerThread>() ;

    /** Create an unbounded thread pool in the current thread group
     * with the current context ClassLoader as the worker thread default
     * ClassLoader.
     */
    public ThreadPoolImpl(String threadpoolName) {
	this( Thread.currentThread().getThreadGroup(), threadpoolName ) ; 
    }

    /** Create an unbounded thread pool in the given thread group
     * with the current context ClassLoader as the worker thread default
     * ClassLoader.
     */
    public ThreadPoolImpl(ThreadGroup tg, String threadpoolName ) {
	this( tg, threadpoolName, getDefaultClassLoader() ) ;
    }

    /** Create an unbounded thread pool in the given thread group
     * with the given ClassLoader as the worker thread default
     * ClassLoader.
     */
    public ThreadPoolImpl(ThreadGroup tg, String threadpoolName, 
	ClassLoader defaultClassLoader) {

        inactivityTimeout = ORBConstants.DEFAULT_INACTIVITY_TIMEOUT;
        minWorkerThreads = 0;
        maxWorkerThreads = Integer.MAX_VALUE;
        workQueue = new WorkQueueImpl(this);
	threadGroup = tg ;
	name = threadpoolName;
	workerThreadClassLoader = defaultClassLoader ;
	initializeMonitoring();
    }
 
    /** Create a bounded thread pool in the current thread group
     * with the current context ClassLoader as the worker thread default
     * ClassLoader.
     */
    public ThreadPoolImpl( int minSize, int maxSize, long timeout, 
	String threadpoolName) {

	this( minSize, maxSize, timeout, threadpoolName, getDefaultClassLoader() ) ;
    }

    /** Create a bounded thread pool in the current thread group
     * with the given ClassLoader as the worker thread default
     * ClassLoader.
     */
    public ThreadPoolImpl( int minSize, int maxSize, long timeout, 
	String threadpoolName, ClassLoader defaultClassLoader ) 
    {
        inactivityTimeout = timeout;
        minWorkerThreads = minSize;
        maxWorkerThreads = maxSize;
        workQueue = new WorkQueueImpl(this);
	threadGroup = Thread.currentThread().getThreadGroup() ;
	name = threadpoolName;
	workerThreadClassLoader = defaultClassLoader ;
        synchronized (workQueue) {
            for (int i = 0; i < minWorkerThreads; i++) {
                createWorkerThread();
            }
        }
	initializeMonitoring();
    }


    // Note that this method should not return until AFTER all threads have died.
    public void close() throws IOException {
        // Copy to avoid concurrent modification problems.
        List<WorkerThread> copy = null ;
        synchronized (workersLock) {
            copy = new ArrayList<WorkerThread>( workers ) ;
        }

        for (WorkerThread wt : copy) {
            wt.close() ;

            while (wt.getState() != Thread.State.TERMINATED) {
                try {
                    wt.join() ;
                } catch (InterruptedException exc) {
                    wrapper.interruptedJoinCallWhileClosingThreadPool( exc, wt, this ) ;
                }
            }
        }

        threadGroup = null ;
    }

    private static ClassLoader getDefaultClassLoader() {
	if (System.getSecurityManager() == null)
	    return Thread.currentThread().getContextClassLoader() ;
	else {
	    final ClassLoader cl = AccessController.doPrivileged( 
		new PrivilegedAction<ClassLoader>() {
		    public ClassLoader run() {
			return Thread.currentThread().getContextClassLoader() ;
		    }
		} 
	    ) ;

	    return cl ;
	}
    }

    // Setup monitoring for this threadpool
    private void initializeMonitoring() {
	// Get root monitored object
	MonitoredObject root = MonitoringFactories.getMonitoringManagerFactory().
		createMonitoringManager(MonitoringConstants.DEFAULT_MONITORING_ROOT, null).
		getRootMonitoredObject();

	// Create the threadpool monitoring root
	MonitoredObject threadPoolMonitoringObjectRoot = root.getChild(
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT);
	if (threadPoolMonitoringObjectRoot == null) {
	    threadPoolMonitoringObjectRoot =  MonitoringFactories.
		    getMonitoredObjectFactory().createMonitoredObject(
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT,
		    MonitoringConstants.THREADPOOL_MONITORING_ROOT_DESCRIPTION);
	    root.addChild(threadPoolMonitoringObjectRoot);
	}
	threadpoolMonitoredObject = MonitoringFactories.
		    getMonitoredObjectFactory().
		    createMonitoredObject(name,
		    MonitoringConstants.THREADPOOL_MONITORING_DESCRIPTION);

	threadPoolMonitoringObjectRoot.addChild(threadpoolMonitoredObject);

	LongMonitoredAttributeBase b1 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS, 
		    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return Long.valueOf(ThreadPoolImpl.this.currentNumberOfThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b1);
	LongMonitoredAttributeBase b2 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_AVAILABLE_THREADS, 
		    MonitoringConstants.THREADPOOL_CURRENT_NUMBER_OF_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return Long.valueOf(ThreadPoolImpl.this.numberOfAvailableThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b2);
	LongMonitoredAttributeBase b3 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS, 
		    MonitoringConstants.THREADPOOL_NUMBER_OF_BUSY_THREADS_DESCRIPTION) {
		public Object getValue() {
		    return Long.valueOf(ThreadPoolImpl.this.numberOfBusyThreads());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b3);
	LongMonitoredAttributeBase b4 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME, 
		    MonitoringConstants.THREADPOOL_AVERAGE_WORK_COMPLETION_TIME_DESCRIPTION) {
		public Object getValue() {
		    return Long.valueOf(ThreadPoolImpl.this.averageWorkCompletionTime());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b4);
	LongMonitoredAttributeBase b5 = new 
	    LongMonitoredAttributeBase(MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT, 
		    MonitoringConstants.THREADPOOL_CURRENT_PROCESSED_COUNT_DESCRIPTION) {
		public Object getValue() {
		    return Long.valueOf(ThreadPoolImpl.this.currentProcessedCount());
		}
	    };
	threadpoolMonitoredObject.addAttribute(b5);

	// Add the monitored object for the WorkQueue
	
	threadpoolMonitoredObject.addChild(
		((WorkQueueImpl)workQueue).getMonitoredObject());
    }

    // Package private method to get the monitored object for this
    // class
    MonitoredObject getMonitoredObject() {
	return threadpoolMonitoredObject;
    }
    
    public WorkQueue getAnyWorkQueue()
    {
	return workQueue;
    }

    public WorkQueue getWorkQueue(int queueId)
	throws NoSuchWorkQueueException
    {
	if (queueId != 0)
	    throw new NoSuchWorkQueueException();
	return workQueue;
    }

    private Thread createWorkerThreadHelper( String name ) { 
	// Thread creation needs to be in a doPrivileged block
	// if there is a non-null security manager for two reasons:
	// 1. The creation of a thread in a specific ThreadGroup
	//    is a privileged operation.  Lack of a doPrivileged
	//    block here causes an AccessControlException
	//    (see bug 6268145).
	// 2. We want to make sure that the permissions associated
	//    with this thread do NOT include the permissions of
	//    the current thread that is calling this method.
	//    This leads to problems in the app server where
	//    some threads in the ThreadPool randomly get
	//    bad permissions, leading to unpredictable
	//    permission errors (see bug 6021011).
	//
	//    A Java thread contains a stack of call frames,
	//    one for each method called that has not yet returned.
	//    Each method comes from a particular class.  The class
	//    was loaded by a ClassLoader which has an associated
	//    CodeSource, and this determines the Permissions
	//    for all methods in that class.  The current
	//    Permissions for the thread are the intersection of
	//    all Permissions for the methods on the stack.
	//    This is part of the Security Context of the thread.
	//
	//    When a thread creates a new thread, the new thread
	//    inherits the security context of the old thread.
	//    This is bad in a ThreadPool, because different
	//    creators of threads may have different security contexts.
	//    This leads to occasional unpredictable errors when
	//    a thread is re-used in a different security context.
	//
	//    Avoiding this problem is simple: just do the thread
	//    creation in a doPrivileged block.  This sets the
	//    inherited security context to that of the code source
	//    for the ORB code itself, which contains all permissions
	//    in either Java SE or Java EE.
	WorkerThread thread = new WorkerThread(threadGroup, name);
        synchronized (workersLock) {
            workers.add( thread ) ;
        }
	
	// The thread must be set to a daemon thread so the
	// VM can exit if the only threads left are PooledThreads
	// or other daemons.  We don't want to rely on the
	// calling thread always being a daemon.
	// Note that no exception is possible here since we
	// are inside the doPrivileged block.
	thread.setDaemon(true);
	
	wrapper.workerThreadCreated( thread, thread.getContextClassLoader() ) ;
	
	thread.start();
	return null ;
    }

    /**
     * To be called from the WorkQueue to create worker threads when none
     * available.
     */
    void createWorkerThread() {
        final String name = getName();
        synchronized (workQueue) {
            try {
                if (System.getSecurityManager() == null) {
                    createWorkerThreadHelper(name) ;
                } else {
                    // If we get here, we need to create a thread.
                    AccessController.doPrivileged(
                            new PrivilegedAction() {
                        public Object run() {
                            return createWorkerThreadHelper(name) ;
                        }
                    }
                    ) ;
                }
            } catch (Throwable t) {
                // Decrementing the count of current worker threads.
                // But, it will be increased in the finally block.
                decrementCurrentNumberOfThreads();
                wrapper.workerThreadCreationFailure(t);
            } finally {
                incrementCurrentNumberOfThreads();
            }
        }
    }
    
    public int minimumNumberOfThreads() {
        return minWorkerThreads;
    }

    public int maximumNumberOfThreads() {
        return maxWorkerThreads;
    }

    public long idleTimeoutForThreads() {
        return inactivityTimeout;
    }
    
    public int currentNumberOfThreads() {
        synchronized (workQueue) {
            return currentThreadCount;
        }
    }

    void decrementCurrentNumberOfThreads() {
        synchronized (workQueue) {
            currentThreadCount--;
        }
    }

    void incrementCurrentNumberOfThreads() {
        synchronized (workQueue) {
            currentThreadCount++;
        }
    }

    public int numberOfAvailableThreads() {
         synchronized (workQueue) {
            return availableWorkerThreads;
        }
    }

    public int numberOfBusyThreads() {
        synchronized (workQueue) {
            return (currentNumberOfThreads() - numberOfAvailableThreads());
        }
    }
    
    public long averageWorkCompletionTime() {
        return (totalTimeTaken.get() / processedCount.get());
    }
    
    public long currentProcessedCount() {
        return processedCount.get();
    }

    public String getName() {
        return name;
    }

    /** 
    * This method will return the number of WorkQueues serviced by the threadpool. 
    */ 
    public int numberOfWorkQueues() {
        return 1;
    } 


    private static int getUniqueThreadId() {
        return ThreadPoolImpl.threadCounter.incrementAndGet();
    }

    /** 
     * This method will decrement the number of available threads
     * in the threadpool which are waiting for work. Called from 
     * WorkQueueImpl.requestWork()
     */ 
    void decrementNumberOfAvailableThreads() {
        synchronized (workQueue) {
            availableWorkerThreads--;
        }
    }
    
    /** 
     * This method will increment the number of available threads
     * in the threadpool which are waiting for work. Called from 
     * WorkQueueImpl.requestWork()
     */ 
    void incrementNumberOfAvailableThreads() {
        synchronized (workQueue) {
            availableWorkerThreads++;
        }
    }

    private class WorkerThread extends Thread implements Closeable
    {
        final private static String THREAD_POOLNAME_PREFIX_STR = "p: ";
        final private static String WORKER_THREAD_NAME_PREFIX_STR = "; w: ";
        final private static String IDLE_STR = "Idle";

        private Work currentWork ;
        private volatile boolean closeCalled = false ;

        WorkerThread(ThreadGroup tg, String threadPoolName) {
	    super(tg, THREAD_POOLNAME_PREFIX_STR + threadPoolName + 
		  WORKER_THREAD_NAME_PREFIX_STR + ThreadPoolImpl.getUniqueThreadId());
            this.currentWork = null;
        }

	private void setClassLoader() {
	    if (System.getSecurityManager() == null)
		setClassLoaderHelper() ;
	    else {
		AccessController.doPrivileged( 
		    new PrivilegedAction<ClassLoader>() {
			public ClassLoader run() {
			    return WorkerThread.this.setClassLoaderHelper() ;
			}
		    } 
		) ;
	    }
	}

	private ClassLoader setClassLoaderHelper() {
	    Thread thr = Thread.currentThread() ;
	    ClassLoader result = thr.getContextClassLoader() ;
	    thr.setContextClassLoader( workerThreadClassLoader ) ;
	    return result ; 
	}

        public synchronized void close() {
            closeCalled = true ;
            interrupt() ;
        }
        
        private void resetClassLoader() {
            ClassLoader currentClassLoader = null;
            try {
                if (System.getSecurityManager() == null) {
                    currentClassLoader = getContextClassLoader() ;
                } else {
                    currentClassLoader = AccessController.doPrivileged(
                        new PrivilegedAction<ClassLoader>() {
                            public ClassLoader run() {
                                return getContextClassLoader();
                            }
                        } 
                    );
                }
            } catch (SecurityException se) {
                throw wrapper.workerThreadGetContextClassloaderFailed(se, this);
            }

            if (workerThreadClassLoader != currentClassLoader) {
                wrapper.workerThreadForgotClassloaderReset(this, 
                    currentClassLoader, workerThreadClassLoader);

                try {
                    setClassLoader() ;
                    wrapper.workerThreadClassloaderReset(this, 
                        currentClassLoader, workerThreadClassLoader);
                } catch (SecurityException se) {
                    wrapper.workerThreadResetContextClassloaderFailed(se, this);
                }
            }
        }

        private void performWork() {
            long start = System.currentTimeMillis();
            try {
                currentWork.doWork();
            } catch (Throwable t) {
                wrapper.workerThreadDoWorkThrowable(t, this);
            }
            long elapsedTime = System.currentTimeMillis() - start;
            totalTimeTaken.addAndGet(elapsedTime);
            processedCount.incrementAndGet();
        }

        public void run() {
            try  {
                while (!closeCalled) {
                    try {
                        currentWork = ((WorkQueueImpl)workQueue).requestWork(
                            inactivityTimeout);
                        if (currentWork == null) 
                            continue;
                    } catch (WorkerThreadNotNeededException toe) {
                        wrapper.workerThreadNotNeeded(this, 
                            currentNumberOfThreads(), minimumNumberOfThreads());
                        closeCalled = true ;
                        continue ;
                    } catch (InterruptedException exc) {
                        wrapper.workQueueThreadInterrupted( exc, super.getName(), 
                            Boolean.valueOf( closeCalled ) ) ;

                        continue ;
                    } catch (Throwable t) {
                        wrapper.workerThreadThrowableFromRequestWork(t, this, 
                                workQueue.getName());
                        
                        continue;
                    } 

                    performWork() ;

		    // set currentWork to null so that the work item can be 
		    // garbage collected without waiting for the next work item.
		    currentWork = null;

                    resetClassLoader() ;
                }
            } catch (Throwable e) {
                // This should not be possible
                wrapper.workerThreadCaughtUnexpectedThrowable(e, this);
            } finally {
                synchronized (workersLock) {
                    workers.remove( this ) ;
                }
            }
        }
    } // End of WorkerThread class
}

// End of file.
