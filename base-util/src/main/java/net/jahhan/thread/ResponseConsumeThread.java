package net.jahhan.thread;

import net.jahhan.context.ApplicationContext;
import net.jahhan.context.InvocationContext;

/**
 * 接口异步线程方法
 * 
 * @author nince
 */
public class ResponseConsumeThread implements Runnable {

	private String actName;

	private long consumeTime;

	public void setActName(String actName) {
		this.actName = actName;
	}

	public void setConsumeTime(long consumeTime) {
		this.consumeTime = consumeTime;
	}

	@Override
	public void run() {
		ApplicationContext applicationContext = ApplicationContext.CTX;
		applicationContext.getThreadLocalUtil().openThreadLocal(new InvocationContext());
		applicationContext.getConsumeManager().addActionConsume(actName, consumeTime);
	}
}