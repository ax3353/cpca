package org.zk.cpca.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AddressTrace {
    private Province province;
    private City city;
    private Area area;
    private Town town;
}