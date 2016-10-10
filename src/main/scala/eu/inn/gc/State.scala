package eu.inn.gc

case class State(
  maxRealTimeElapsed: Double,
  totalRealTimeElapsed: Double,
  sumSquaresRealTimeElapsed: Double,
  totalBytesReclaimed: Double,
  count: Double,
  startNanos: Long
) {

  def this() = this(
    maxRealTimeElapsed = 0,
    totalRealTimeElapsed = 0,
    sumSquaresRealTimeElapsed = 0,
    totalBytesReclaimed = 0,
    count = 0,
    startNanos = System.nanoTime()
  )

  def this(extraElapsed: Double, extraBytes: Double, prev: State) = this(
    maxRealTimeElapsed = Math.max(prev.maxRealTimeElapsed, extraElapsed),
    totalRealTimeElapsed = prev.totalRealTimeElapsed + extraElapsed,
    sumSquaresRealTimeElapsed = prev.sumSquaresRealTimeElapsed + (extraElapsed * extraElapsed),
    totalBytesReclaimed = prev.totalBytesReclaimed + extraBytes,
    count = prev.count + 1,
    startNanos = prev.startNanos
  )
}