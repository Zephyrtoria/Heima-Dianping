package com.github.zephyrtoria.hmdp.controller;


import com.github.zephyrtoria.hmdp.entity.result.Result;
import com.github.zephyrtoria.hmdp.service.IFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 前端控制器
 * </p>
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    @PutMapping("{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followUserId, @PathVariable("isFollow") Boolean isFollow) {
        return followService.setFollower(followUserId, isFollow);
    }

    @GetMapping("or/not/{id}")
    public Result isFollowing(@PathVariable("id") Long followUserId) {
        return followService.isFollowing(followUserId);
    }
}
