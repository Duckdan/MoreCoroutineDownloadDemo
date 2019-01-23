package com.study.download.morecoroutinedownloaddemo

import android.content.pm.PackageManager
import android.os.AsyncTask
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import com.study.download.morecoroutinedownloaddemo.utlis.PermissionCheckUtils
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.experimental.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    var path = "http://192.168.31.105:8080/HotSpot/tomcat.avi"
    //协程数量
    var coroutineCount = 3
    var coroutineRunningCount = coroutineCount
    //权限数组
    var permissionArray = arrayOf<String>(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    fun downloadByCoroutine(view: View) {
        val size = PermissionCheckUtils.checkActivityPermissions(this, permissionArray, 100, null)
        if (size == 0) {
            //runBlocking {  } 以此种方式启动的协程仍旧运行于main线程中，所以联网操作不可以在此处完成
            //用于执行协程任务，通常用于启动最外层的协程
            startDownload()
        }

    }

    /**
     * 开始下载
     */
    private fun startDownload() {
        coroutineRunningCount = coroutineCount
        pb_1.progress = 0
        pb_2.progress = 0
        pb_3.progress = 0

        AsyncTask.THREAD_POOL_EXECUTOR.execute {  }
        //协程
//        async {
//            println("当前async的线程名称为：${Thread.currentThread().name}")
//            beforeDownload()
//        }
        //使用async{}.await()可以返回协程执行的结果
        //线程
//        Thread {
//            beforeDownload()
//        }.start()
        //线程池
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            beforeDownload()
        }
    }

    private fun beforeDownload() {
        println("当前线程名称为：${Thread.currentThread().name}")
        //创建与文件地址绑定的连接
        var url = URL(path)
        //打开文件连接对象
        val openConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
        //设置请求方式
        openConnection.requestMethod = "GET"
        //设置连接时间
        openConnection.connectTimeout = 5000
        //获取网络连接的响应码
        val responseCode = openConnection.responseCode
        //响应码等于200，说明创建连接成功
        if (responseCode == 200) {
            //获取文件长度
            val contentLength = openConnection.contentLength
            println("获取到的文件长度为：$contentLength")
            //先创建一个跟将要下载文件大小相同的文件
            val sdPath = Environment.getExternalStorageDirectory().absolutePath
            //使用rw模式可以使用缓冲区，往硬盘中写数据
            val filePath = "$sdPath/tomcat.avi"
            var file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
            var raf = RandomAccessFile(filePath, "rw")
            //设置创建文件的大小
            raf.setLength(contentLength.toLong())
            //这句话执行完成之后将会生成一个tomcat.avi文件，该文件的大小跟上一句赋值的长度一样大
            raf.close()

            var blockSize = contentLength / coroutineCount
            for (i in 0 until coroutineCount) {
                //开始下载的文件坐标
                var start = i * blockSize
                //下载截止的文件坐标
                var end = (i + 1) * blockSize - 1
                //最后一个线程下载截止的文件坐标
                if (i == coroutineCount - 1) {
                    end = contentLength - 1
                }
                println("当前协程的id$i,文件下载的起始坐标$start,文件下载的截止坐标$end")
                //启动相应协程，实现多协程下载
                //                    downloadByLaunch(start, end, sdPath, i)
                downloadByThread(start, end, sdPath, i)
            }
        }
    }

    private fun downloadByThread(start: Int, end: Int, sdPath: String?, coroutineId: Int) {
//        Thread {
//            reallyDownload(start, end, sdPath, coroutineId)
//        }.start()
        AsyncTask.THREAD_POOL_EXECUTOR.execute {
            reallyDownload(start, end, sdPath, coroutineId)
        }
    }

    private fun downloadByLaunch(start: Int, end: Int, sdPath: String?, coroutineId: Int) {
        launch {
            reallyDownload(start, end, sdPath, coroutineId)
        }
    }

    private fun reallyDownload(start: Int, end: Int, sdPath: String?, coroutineId: Int) {
        val name = Thread.currentThread().name
        Log.e("thread", name)
        //重新创建连接，进行指定位置下载
        //创建与文件地址绑定的连接
        var url = URL(path)
        //打开文件连接对象
        val openConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
        //设置请求方式
        openConnection.requestMethod = "GET"
        //设置连接时间
        openConnection.connectTimeout = 5000
        //在请求头中封装客户端所需要的数据
        openConnection.setRequestProperty("range", "bytes=$start-$end")
        //获取网络连接的响应码
        val responseCode = openConnection.responseCode
        //此时返回206的响应码才算是成功的
        if (responseCode == 206) {
            var inputStream = openConnection.inputStream
            //创建文件
            var raf = RandomAccessFile("$sdPath/tomcat.avi", "rw")
            //跳到指定位置
            raf.seek(start.toLong())

            var len = -1
            //设定缓冲区
            var buf = ByteArray(1024)
            var flag = true
            var progress = 0
            while (flag) {
                //读取数据，返回下标
                len = inputStream.read(buf)
                //TODO 可以在此处记录下载文件的坐标，从而实现断点下载
                flag = len != -1
                //写数据
                if (flag) {
                    raf.write(buf, 0, len)
                    progress += len
                }

                when (coroutineId) {
                    0 -> {
                        pb_1.max = end - start
                        pb_1.progress = progress
                        println("当前正在运行的协程号码是$coroutineId,progress是$progress")
                    }
                    1 -> {
                        pb_2.max = end - start
                        pb_2.progress = progress
                        println("当前正在运行的协程号码是$coroutineId,progress是$progress")
                    }
                    2 -> {
                        pb_3.max = end - start
                        pb_3.progress = progress
                        println("当前正在运行的协程号码是$coroutineId,progress是$progress")
                    }
                }

            }
            raf.close()
            //同步锁，因为操作的是同一资源，为了避免操作错乱，所以此处的锁必须是唯一锁和同一锁
//            synchronized(MainActivity::class) {
//                coroutineRunningCount--
//                if (coroutineRunningCount == 0) {
//                    runOnUiThread {
//                        Toast.makeText(this@MainActivity, "下载完成", Toast.LENGTH_SHORT).show()
//                    }
//                } else {
//                    println("当前正在运行的协程号码是$coroutineRunningCount")
//                }
//            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        var flag = true
        for (i in grantResults) {
            //判断是否赋予权限的标记
            flag = if (i == PackageManager.PERMISSION_GRANTED) {
                flag && true
            } else {
                flag && false
            }
        }
        //如果权限赋予成功，那么久开始下载
        if (flag) {
            startDownload()
        }
    }
}
