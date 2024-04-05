package edu.wwu.eas.automation

class ReposyncException(m: String, e: Throwable) extends RuntimeException(m, e):

  def this(m: String) =
    this(m, null)

  def this(e: Throwable) =
    this(e.getMessage(), e)
