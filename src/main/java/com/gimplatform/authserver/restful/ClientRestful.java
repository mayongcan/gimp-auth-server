package com.gimplatform.authserver.restful;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.MapUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.gimplatform.core.common.VersionCode;
import com.gimplatform.core.entity.ClientVersion;
import com.gimplatform.core.service.ClientVersionService;
import com.gimplatform.core.utils.RestfulRetUtils;
import com.gimplatform.core.utils.VersionUtils;

/**
 * 用户相关的Restful接口
 * 
 * @author zzd
 *
 */
@RestController
@RequestMapping("client")
public class ClientRestful {

	protected static final Logger logger = LogManager.getLogger(ClientRestful.class);

	@Autowired
	private ClientVersionService clientVersionService;

	/**
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/versionUpdate", method = RequestMethod.GET)
	public JSONObject versionUpdate(HttpServletRequest request, @RequestParam Map<String, Object> params) {
		JSONObject json = new JSONObject();
		try {
			Pageable pageable = new PageRequest(0, 15);
			ClientVersion ClientVersion = new ClientVersion();
			ClientVersion.setName(MapUtils.getString(params, "appName"));
			Page<ClientVersion> list = clientVersionService.getList(pageable, ClientVersion);
			List<ClientVersion> clientList = list.getContent();
			if(clientList.size() == 0){
				return RestfulRetUtils.getRetSuccess(clientList);
			}
			List<VersionCode> vrlist = new ArrayList<VersionCode>();

			for (int i = 0; i < clientList.size(); i++) {
				ClientVersion tmpClient = (ClientVersion) clientList.get(i);
				String[] version = tmpClient.getVersion().split("\\.");
				VersionCode vc = new VersionCode(version[0], version[1], version[2]);
				vc.setPosition(i);
				vrlist.add(vc);

			}
			VersionUtils comparator = new VersionUtils();
			Collections.sort(vrlist, comparator);
			VersionCode temp = (VersionCode) vrlist.get(vrlist.size() - 1);
			logger.info("版本信息:" + temp.getVerOne() + "," + temp.getVerTwo() + "," + temp.getVerThree() + temp.getPosition());

			ClientVersion tmpClient1 = (ClientVersion) clientList.get(temp.getPosition());

			List<Map<String, Object>> retList = new ArrayList<>();
			Map<String, Object> map = new HashMap<>();
			map.put("version", tmpClient1.getVersion());
			map.put("verDesc", tmpClient1.getVerDesc());
			map.put("fileSize", tmpClient1.getFileSize());
			map.put("url", tmpClient1.getUrl());
			map.put("isOption", tmpClient1.getIsOption());
			retList.add(map);

			json = RestfulRetUtils.getRetSuccess(retList);

		} catch (Exception e) {
			json = RestfulRetUtils.getErrorMsg("31001", "获取最新版本失败");
			logger.error(e.getMessage(), e);
		}
		return json;
	}

}
