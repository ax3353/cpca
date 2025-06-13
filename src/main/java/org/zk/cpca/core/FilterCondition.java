package org.zk.cpca.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author zk
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FilterCondition {
    private String province;
    private String city;
    private String area;
}