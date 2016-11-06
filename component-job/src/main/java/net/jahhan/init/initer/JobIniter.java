package net.jahhan.init.initer;

import javax.inject.Inject;

import com.google.inject.Injector;

import net.jahhan.context.ApplicationContext;
import net.jahhan.init.BootstrapInit;
import net.jahhan.init.InitAnnocation;
import net.jahhan.job.api.TaskProcessor;

@InitAnnocation(initOverWait = false)
public class JobIniter implements BootstrapInit {
	@Inject
	private Injector injector;

	@Override
	public void execute() {
		TaskProcessor p = injector.getInstance(TaskProcessor.class);

		try {
			ApplicationContext applicationContext = null;
			while (null == applicationContext) {
				Thread.sleep(1000);
				applicationContext = ApplicationContext.CTX;
			}
			p.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}