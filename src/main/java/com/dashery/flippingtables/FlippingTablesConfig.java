/*
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.dashery.flippingtables;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("flippingtables")
public interface FlippingTablesConfig extends Config
{
	@ConfigItem(
		position = 1,
		keyName = "buyWindow",
		name = "Buy window",
		description = "Buy window time"
	)
	default int buyWindow()
	{
		return 8;
	}

	@ConfigItem(
			position = 2,
			keyName = "sellWindow",
			name = "Sell window",
			description = "Sell window time"
	)
	default int sellWindow()
	{
		return 8;
	}

	@ConfigItem(
			position = 3,
			keyName = "moneyAvailable",
			name = "Money available",
			description = "Money available for flipping"
	)
	default int moneyAvailable()
	{
		return 1;
	}

	@ConfigItem(
			position = 4,
			keyName = "slotsAvailable",
			name = "Slots available",
			description = "Slots available for flipping"
	)
	default int slotsAvailable()
	{
		return 8;
	}

	@ConfigItem(
			position = 5,
			keyName = "members",
			name = "Members",
			description = "Ye be a member be ye?"
	)
	default boolean members()
	{
		return true;
	}
}