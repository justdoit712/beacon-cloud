package com.cz.webmaster.service.impl;

import com.cz.webmaster.entity.SmsUser;
import com.cz.webmaster.entity.SmsUserExample;
import com.cz.webmaster.mapper.SmsUserMapper;
import com.cz.webmaster.service.SmsUserService;
import org.apache.shiro.crypto.hash.SimpleHash;
import org.apache.shiro.util.ByteSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author cz
 * @description
 */
@Service
public class SmsUserServiceImpl implements SmsUserService {

    @Autowired
    private SmsUserMapper userMapper;

    @Override
    public SmsUser findByUsername(String username) {
        SmsUserExample example = new SmsUserExample();
        SmsUserExample.Criteria criteria = example.createCriteria();
        criteria.andUsernameEqualTo(username);
        criteria.andIsDeleteEqualTo((byte) 0);
        List<SmsUser> list = userMapper.selectByExample(example);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    @Override
    public List<SmsUser> findByKeyword(String keyword) {
        SmsUserExample example = buildExampleByKeyword(keyword);
        example.setOrderByClause("id desc");
        return userMapper.selectByExample(example);
    }

    @Override
    public long countByKeyword(String keyword) {
        SmsUserExample example = buildExampleByKeyword(keyword);
        return userMapper.countByExample(example);
    }

    @Override
    public SmsUser findById(Integer id) {
        return userMapper.selectByPrimaryKey(id);
    }

    @Override
    public boolean save(SmsUser user) {
        if (user == null) {
            return false;
        }
        Date now = new Date();
        user.setCreated(now);
        user.setUpdated(now);
        if (user.getIsDelete() == null) {
            user.setIsDelete((byte) 0);
        }
        if (!StringUtils.hasText(user.getSalt())) {
            user.setSalt(UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        if (!StringUtils.hasText(user.getPassword())) {
            user.setPassword("123456");
        }
        user.setPassword(encodePassword(user.getPassword(), user.getSalt()));
        return userMapper.insertSelective(user) > 0;
    }

    @Override
    public boolean update(SmsUser user) {
        if (user == null || user.getId() == null) {
            return false;
        }
        SmsUser dbUser = userMapper.selectByPrimaryKey(user.getId());
        if (dbUser == null) {
            return false;
        }
        if (StringUtils.hasText(user.getPassword())) {
            String salt = StringUtils.hasText(dbUser.getSalt()) ? dbUser.getSalt() : UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            user.setSalt(salt);
            user.setPassword(encodePassword(user.getPassword(), salt));
        } else {
            user.setPassword(null);
            user.setSalt(null);
        }
        user.setUpdated(new Date());
        return userMapper.updateByPrimaryKeySelective(user) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteBatch(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (Integer id : ids) {
            SmsUser user = new SmsUser();
            user.setId(id);
            user.setIsDelete((byte) 1);
            user.setUpdated(now);
            if (userMapper.updateByPrimaryKeySelective(user) <= 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean updatePassword(Integer userId, String oldPassword, String newPassword) {
        if (userId == null || !StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return false;
        }
        SmsUser user = userMapper.selectByPrimaryKey(userId);
        if (user == null || !StringUtils.hasText(user.getSalt()) || !StringUtils.hasText(user.getPassword())) {
            return false;
        }
        String oldPasswordCipher = encodePassword(oldPassword, user.getSalt());
        if (!oldPasswordCipher.equals(user.getPassword())) {
            return false;
        }
        SmsUser updateUser = new SmsUser();
        updateUser.setId(userId);
        updateUser.setPassword(encodePassword(newPassword, user.getSalt()));
        updateUser.setUpdated(new Date());
        return userMapper.updateByPrimaryKeySelective(updateUser) > 0;
    }

    private SmsUserExample buildExampleByKeyword(String keyword) {
        SmsUserExample example = new SmsUserExample();
        SmsUserExample.Criteria criteria = example.createCriteria();
        criteria.andIsDeleteEqualTo((byte) 0);
        if (StringUtils.hasText(keyword)) {
            criteria.andUsernameLike("%" + keyword.trim() + "%");
        }
        return example;
    }

    private String encodePassword(String password, String salt) {
        return new SimpleHash("MD5", password, ByteSource.Util.bytes(salt), 1024).toHex();
    }
}
