package com.treeeducation.ioas.official.lead;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class OfficialLeadDtos {

    public record CreateLeadRequest(
            @NotBlank(message = "姓名不能为空")
            @Size(max = 80)
            String name,

            @NotBlank(message = "年龄不能为空")
            @Size(max = 20)
            String age,

            @NotBlank(message = "学历不能为空")
            @Size(max = 120)
            String education,

            @NotBlank(message = "所在城市不能为空")
            @Size(max = 120)
            String city,

            @NotBlank(message = "电话不能为空")
            @Size(max = 40)
            String phone,

            @Size(max = 80)
            String wechat,

            @NotBlank(message = "意向国家不能为空")
            @Size(max = 120)
            String destination,

            @NotBlank(message = "预算不能为空")
            @Size(max = 80)
            String budget,

            @Size(max = 2000)
            String remark,

            @NotBlank(message = "来源不能为空")
            @Size(max = 120)
            String source
    ) {}

    public record LeadResponse(
            Long id,
            String name,
            String phone,
            String wechat,
            String destination,
            String budget,
            OfficialLeadStatus status,
            Instant createdAt
    ) {}
}
