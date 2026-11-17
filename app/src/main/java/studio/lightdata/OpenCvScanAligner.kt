package studio.lightdata

import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.*

/** Sensor-seeded, pyramidal Lucas–Kanade registration with a robust similarity fit. */
internal object OpenCvScanAligner : ScanAligner {
    val available: ScanAligner? by lazy { if (OpenCVLoader.initLocal()) this else null }

    override fun align(w: Int, h: Int, reference: ByteArray, incoming: ByteArray, mask: ByteArray): ScanWarp? {
        if (min(w,h) < 20) return null
        val allocations = mutableListOf<Mat>()
        fun <T: Mat> T.owned(): T { allocations.add(this); return this }
        fun gray(bytes: ByteArray) = Mat(h,w,CvType.CV_8UC1).owned().apply { put(0,0,bytes) }
        try {
            val ref = gray(reference)
            val src = gray(incoming)
            val valid = gray(mask)
            // A corner must belong to the image, not the boundary of the warped atlas.
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(13.0,13.0)).owned()
            Imgproc.erode(valid,valid,kernel)
            val corners = MatOfPoint().owned()
            Imgproc.goodFeaturesToTrack(ref,corners,100,.015,5.0,valid)
            if (corners.total() < 8) return null
            val pts = corners.toArray()
            val old = MatOfPoint2f(*pts).owned()
            val next = MatOfPoint2f().owned()
            val back = MatOfPoint2f().owned()
            val forwardStatus = MatOfByte().owned()
            val backStatus = MatOfByte().owned()
            val errors = MatOfFloat().owned()
            val window = Size(15.0,15.0)
            Video.calcOpticalFlowPyrLK(ref,src,old,next,forwardStatus,errors,window,2)
            Video.calcOpticalFlowPyrLK(src,ref,next,back,backStatus,errors,window,2)
            val tracked = next.toArray()
            val returned = back.toArray()
            val forwardOk = forwardStatus.toArray()
            val backOk = backStatus.toArray()
            val good = pts.indices.filter { i ->
                forwardOk[i].toInt()!=0 && backOk[i].toInt()!=0 &&
                    tracked[i].x in 0.0..(w-1.0) && tracked[i].y in 0.0..(h-1.0) &&
                    hypot(pts[i].x-returned[i].x,pts[i].y-returned[i].y) < 1.2
            }
            if (good.size < 8) return null
            val from = MatOfPoint2f(*good.map { pts[it] }.toTypedArray()).owned()
            val to = MatOfPoint2f(*good.map { tracked[it] }.toTypedArray()).owned()
            val inliers = Mat().owned()
            val fit = Calib3d.estimateAffinePartial2D(from,to,inliers,Calib3d.RANSAC,1.6).owned()
            if (fit.empty() || Core.countNonZero(inliers) < max(7,(good.size*.65).toInt())) return null
            val m = DoubleArray(6)
            fit.get(0,0,m)
            if (hypot(m[0],m[3]) !in .95..1.05 || abs(m[3]) > .08 ||
                hypot(m[2],m[5]) > min(w,h)*.15) return null
            return ScanWarp(m[0],m[3],m[2],m[5])
        } catch (_: CvException) {
            return null
        } finally {
            allocations.asReversed().forEach { it.release() }
        }
    }
}