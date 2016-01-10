package com.game.robot.kernal;

import java.io.File;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.json.JSONObject;
import org.apache.commons.io.FileUtils;

import com.game.part.util.PackageUtil;
import com.game.robot.RobotLog;
import com.game.robot.kernal.RobotConf.TestModule;

/**
 * 机器人主程序
 * 
 * @author hjj2019
 * @since 2015/5/14
 * 
 */
public final class RobotMain {
	/** JSON 配置文件 */
	public String _jsonConfFileName = "etc/robot.json";
	/** 功能模块类定义字典 */
	private final Map<String, Set<Class<?>>> _moduleClazzDefMap = new ConcurrentHashMap<>();
	/** 机器人字典 */
	private final Map<String, Robot> _robotMap = new ConcurrentHashMap<>();
	
	/**
	 * 开始运行机器人
	 * 
	 */
	public void start() {
		// 记录日志信息
		RobotLog.LOG.info("++++ 启动机器人项目 ++++");

		// 创建配置对象
		RobotLog.LOG.info(MessageFormat.format(
			"加载配置文件 : {0}", 
			this._jsonConfFileName
		));
		final RobotConf confObj = createConfObj(this._jsonConfFileName);

		if (confObj == null) {
			// 如果配置对象为空, 
			// 则直接退出!
			RobotLog.LOG.error("创建配置对象失败");
			return;
		}

		// 事先扫描 gameServer 项目, 
		// 注册所有的 GC 消息!
		RobotGCMsgRecognizer.OBJ.scanAllModule();
		// 扫描 moduleImpl 下得所有模块
		this.scanAllModuleImpl(confObj);

		for (int i = confObj._startPId; i <= confObj._endPId; i++) {
			// 创建机器人对象
			Robot newRobot = new Robot(String.valueOf(i), confObj._userPassword);
			
			// 将机器人添加到字典中
			this._robotMap.put(
				newRobot._userName, 
				newRobot
			);

			// 创建聚焦模块链表
			newRobot.putCurrFocusModule(this.createModuleChain(confObj));
			// 游戏服务器配置
			newRobot._gameServerIpAddr = confObj._gameServerIpAddr;
			newRobot._gameServerPort = confObj._gameServerPort;
			newRobot._gameServerName = confObj._gameServerName;

			// 记录日志信息
			RobotLog.LOG.info(MessageFormat.format(
				"启动机器人 {0}", 
				newRobot._userName
			));
			// 启动机器人
			newRobot.start();
		}

		try {
			while (true) {
				if (Robot.ROBOT_COUNTER.get() <= 0) {
					// 终止 Netty 工作线程
					Robot.NETTY_WORK_GROUP.shutdownGracefully();
                    break;
				} else {
					// 歇 200 毫秒
					Thread.sleep(200L);
				}
			}
		} catch (Exception ex) {
			// 记录异常信息
			RobotLog.LOG.error(ex.getMessage(), ex);
		}
	}

	/**
	 * 创建配置对象
	 * 
	 * @param jsonConfFileName 
	 * @return 
	 * 
	 */
	private static RobotConf createConfObj(String jsonConfFileName) {
		try {
			// 读取配置文件获取文本字符串
			String textStr = FileUtils.readFileToString(
				new File(jsonConfFileName), 
				Charset.forName("utf8")
			);
			// 创建配置对象
			RobotConf confObj = RobotConf.create(JSONObject.fromObject(textStr));
			// 返回配置对象
			return confObj;
		} catch (Exception ex) {
			// 记录错误日志
			RobotLog.LOG.error(ex.getMessage(), ex);
			return null;
		}
	}

	/**
	 * 扫描所有的模块实现
	 * 
	 * @param confObj
	 * 
	 */
	private void scanAllModuleImpl(RobotConf confObj) {
		if (confObj == null) {
			// 如果参数对象为空, 
			// 则直接退出!
			return;
		}

		for (TestModule moduleConf : confObj._testModuleList) {
			if (moduleConf == null) {
				// 如果模块配置为空, 
				// 则直接跳过!
				continue;
			}

			// 获取包名称
			final String packageName = "com.game.robot.moduleImpl."
				+ moduleConf._currModule;

			// 获取 "准备类定义" 
			Set<Class<?>> readyClazzDefSet = PackageUtil.listSubClazz(
				packageName, 
				AbstractModuleReady.class
			);
	
			// 添加到输出字典
			this._moduleClazzDefMap.put(
				packageName + ".R", 
				readyClazzDefSet
			);

			// 获取 "消息处理器类定义" 
			Set<Class<?>> handlerClazzDefSet = PackageUtil.listSubClazz(
				packageName, 
				AbstractGCMsgHandler.class
			);

			// 添加到输出字典
			this._moduleClazzDefMap.put(
				packageName + ".H", 
				handlerClazzDefSet
			);
		}
	}

	/**
	 * 创建聚焦模块链表
	 * 
	 * @param confObj 
	 * @return
	 * 
	 */
	private FocusModule createModuleChain(RobotConf confObj) {
		if (confObj == null || 
			confObj._testModuleList == null) {
			// 如果参数对象为空, 
			// 则直接退出!
			return null;
		}

		// 模块字典
		Map<String, FocusModule> moduleMap = new HashMap<>();

		// 定义头指针和计数器
		FocusModule head = null;

		for (TestModule moduleConf : confObj._testModuleList) {
			if (moduleConf == null) {
				// 如果模块配置为空, 
				// 则直接跳过!
				continue;
			}

			// 创建聚焦模块
			FocusModule currModule = new FocusModule();
			// 设置模块准备对象
			currModule._moduleReady = this.createModuleReadyObj(moduleConf);
			// 添加所有的消息处理器
			currModule.addAllGCMsgHandler(
				this.createGCMsgHandlerObjSet(moduleConf)
			);

			if (head == null) {
				head = currModule;
			}

			// 添加当前模块到字典
			moduleMap.put(
				moduleConf._currModule, 
				currModule
			);
		}

		// 
		// 以下代码的主要功能是建立模块之间的关联!
		// 
		for (TestModule moduleConf : confObj._testModuleList) {
			if (moduleConf._nextModule != null && 
				moduleConf._nextModule.isEmpty() == false) {
				// 获取当前模块
				FocusModule currModule = moduleMap.get(moduleConf._currModule);
				// 获取当前模块的下一个模块
				FocusModule nextModule = moduleMap.get(moduleConf._nextModule);

				if (currModule != null &&
					nextModule != null) {
					// 建立关联
					currModule.setNext(nextModule);
				}
			}
		}

		return head;
	}
 
	/**
	 * 创建模块准备对象
	 * 
	 * @param moduleConf
	 * @return
	 * 
	 */
	private AbstractModuleReady createModuleReadyObj(TestModule moduleConf) {
		if (moduleConf == null) {
			// 如果参数对象为空, 
			// 则直接退出!
			return null;
		}

		// 获取包名称
		final String packageName = "com.game.robot.moduleImpl."
			+ moduleConf._currModule;

		// 获取 "准备类定义" 
		Set<Class<?>> clazzDefSet = this._moduleClazzDefMap.get(
			packageName + ".R"
		);

		if (clazzDefSet == null || 
			clazzDefSet.isEmpty()) {
			return null;
		}

		// 获取类定义
		Class<?> clazzDef = clazzDefSet.iterator().next();

		try {
			// 创建 Ready 类对象
			Object obj = clazzDef.newInstance();
			// 强转并返回!
			return (AbstractModuleReady)obj;
		} catch (Exception ex) {
			// 记录错误日志
			RobotLog.LOG.error(ex.getMessage(), ex);
			return null;
		}
	}

	/**
	 * 创建 GC 消息处理器集合
	 * 
	 * @param moduleConf
	 * @return
	 * 
	 */
	private Set<AbstractGCMsgHandler<?>> createGCMsgHandlerObjSet(TestModule moduleConf) {
		if (moduleConf == null) {
			// 如果参数对象为空, 
			// 则直接退出!
			return null;
		}

		// 获取包名称
		final String packageName = "com.game.robot.moduleImpl."
			+ moduleConf._currModule;

		// 获取 "准备类定义" 
		Set<Class<?>> clazzDefSet = this._moduleClazzDefMap.get(
			packageName + ".H"
		);

		if (clazzDefSet == null || 
			clazzDefSet.isEmpty()) {
			return null;
		}

		// 处理器集合
		Set<AbstractGCMsgHandler<?>> handlerObjSet = new HashSet<>();

		for (Class<?> clazzDef : clazzDefSet) {
			if (clazzDef == null) {
				// 如果类定义为空, 
				// 则直接跳过!
				continue;
			}

			try {
				// 创建 GCHandler 类对象
				Object obj = clazzDef.newInstance();
				// 强制转型并添加到集合
				handlerObjSet.add((AbstractGCMsgHandler<?>)obj);
			} catch (Exception ex) {
				// 记录错误日志
				RobotLog.LOG.error(ex.getMessage(), ex);
			}
		}

		return handlerObjSet;
	}
}
