package com.example.parent;

import java.util.StringJoiner;

public class SampleConstants {

  public static final int POLL_THREAD_COUNT = 1;

  public static final String DOMAIN = "sample";

  private static final String TASK_LIST_PARENT = "HelloParentX";
  private static final String TASK_LIST_CHILD = "HelloChildX";

  public static String getTaskListParent(int i) {
    return new StringJoiner("_").add(TASK_LIST_PARENT).add(String.valueOf(i)).toString();
  }

  public static String getTaskListParent() {
    return getTaskListParent(1);
  }

  public static String getTaskListChild(int i) {
    return new StringJoiner("_").add(TASK_LIST_CHILD).add(String.valueOf(i)).toString();
  }

  public static String getTaskListChild() {
    return getTaskListChild(1);
  }

  private static String getTaskListCompensation(int i) {
    return new StringJoiner("_")
        .add(TASK_LIST_CHILD)
        .add("Compensation")
        .add(String.valueOf(i))
        .toString();
  }
}
