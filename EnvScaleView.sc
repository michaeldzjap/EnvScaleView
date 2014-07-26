EnvScaleView {
	var <view,<background,<font,<drawFunc,<horzGridDist,<maxHorzGridDist,<minHorzGridDist,<numVertGridLines,<gridColor,<gridWidth,<showHorzAxis,<showVertAxis,<breakPointSize,<curvePointRadius,<domainMode,<minRange,<maxRange,<unitMode,<scaleResponsiveness,<env,envData,envView,rangeView,settingsView,numGridLinesPerUnit,vhorzGridDist,selGridLineCoord,loopStartNode,loopEndNode,timeIncr,timeStep,uI;
	classvar unitStep;

	*initClass {
		Class.initClassTree(Array);
		Class.initClassTree(IdentityDictionary);
		unitStep = IdentityDictionary.new.add(\time -> [
			100.0,50.0,20.0,10.0,5.0,2.0,1.0,0.5,0.2,0.1,0.05,0.02,0.01,0.005
		]).add(\tempo -> [
			IdentityDictionary.new.add(\measure -> [64,1]).add(\time -> 128.0),IdentityDictionary.new.add(\measure -> [16,1]).add(\time -> 32.0),IdentityDictionary.new.add(\measure -> [8,1]).add(\time -> 16.0),IdentityDictionary.new.add(\measure -> [4,1]).add(\time -> 5.0),IdentityDictionary.new.add(\measure -> [4,1]).add(\time -> 4.0),IdentityDictionary.new.add(\measure -> [1,1]).add(\time -> 2.0),IdentityDictionary.new.add(\measure -> [1,2]).add(\time -> 1.0),IdentityDictionary.new.add(\measure -> [1,4]).add(\time -> 0.5),IdentityDictionary.new.add(\measure -> [1,8]).add(\time -> 0.25),IdentityDictionary.new.add(\measure -> [1,16]).add(\time -> 0.125),IdentityDictionary.new.add(\measure -> [1,32]).add(\time -> 0.0625),IdentityDictionary.new.add(\measure -> [1,64]).add(\time -> 0.03125),IdentityDictionary.new.add(\measure -> [1,128]).add(\time -> 0.015625),IdentityDictionary.new.add(\measure -> [1,256]).add(\time -> 0.0078125)
		])
	}

	*new { arg parent,bounds,env;
		bounds = bounds ? (parent.notNil.if { parent.view.bounds } { nil } ? Rect(100,300,600,300));
		env = env ? Env([0,1,1,0],[0,0.5,0],[0,0,0],nil,nil);
		env.curves.respondsTo(\at).not.if { env.curves = { env.curves } ! env.times.size };
		^super.new.init(parent,bounds,env)
	}

	init { arg parent,bounds,env;
		var prevPoint,brPtModeMenu,breakPointMode,ctlKeyDown = false,remNumGridLines,scaleCount = 0,scaleRespCount = 0,onBreakPoint,onCurvePoint,dbreakPointXCoords,initBreakPointTime,dinitBreakPointTime,prevHeight;

		this.env_(env,false);

		uI = 8;
		numGridLinesPerUnit = 8;
		this.prTimeStep_(unitStep[\time][uI]);
		timeIncr = timeStep/numGridLinesPerUnit;

		loopStartNode = 1;
		loopEndNode = 2;

		// initialize grid vars
		this.gridWidth_(0.125,false);
		this.gridColor_(Color.grey,false);
		this.numVertGridLines_(20,false);
		this.horzGridDist_(20,false);
		this.maxHorzGridDist_(horzGridDist*1.8,false);
		this.minHorzGridDist_(horzGridDist*0.5,false);
		/*selGridLineCoord = ((bounds.width/horzGridDist).div(2)*horzGridDist)@(bounds.width/horzGridDist/numGridLinesPerUnit*0.5*unitStep[uI][\time]);
		selGridLineCoord.postln;*/
		this.prSelGridLineCoord_(320@0.4);
		remNumGridLines = 0;
		this.prVhorzGridDist_(horzGridDist);

		// initialize axes vars
		this.showHorzAxis_(true,false);
		this.showVertAxis_(true,false);

		// initialize break point and curve point vars
		this.breakPointSize_(7,false);
		this.curvePointRadius_(2.5,false);

		// initialize other vars
		this.font_(Font("Courier",12),false);
		this.unitMode_(\time,false);
		this.domainMode_(\unipolar,false);
		this.minRange_(0,false);
		this.maxRange_(1,false);
		this.scaleResponsiveness_(3);

		prevHeight = 234;
		envData.height = 234;   // set height var to correct initial height of envView
		envData.createEnvPlotData;

		view = View(parent,bounds).resize_(5).background_(Color.white);

		rangeView = UserView(parent,Rect(bounds.left,bounds.top,bounds.width*0.05,bounds.height)).background_(parent.notNil.if { parent.background } { nil }).resize_(5);

		envView = UserView(parent,rangeView.bounds.right,bounds.top,bounds.width - rangeView.bounds.width,bounds.height).background_(Color(218/255,232/255,238/255,1)).resize_(5).drawFunc_({ |me|
			var pos,vertGridDist,lineResolution = 50;

			// when the user resizes the envelope view, makes sure that all break and curve points get scaled proportionally in the vertical direction
			(me.bounds.height != prevHeight).if {
				envData.height = me.bounds.height;
				envData.updateAllBreakPointCoordsY;
				envData.updateAllCurvePointCoordsY
			};
			prevHeight = me.bounds.height;

			// draw horizontal lines (fixed)
			Pen.width = gridWidth;
			Pen.strokeColor = gridColor;
			vertGridDist = me.bounds.height/numVertGridLines;
			(me.bounds.height/vertGridDist).ceil do: { |i|
				var y = i*vertGridDist;
				Pen.moveTo(0@y);
				Pen.lineTo(me.bounds.width@y)
			};
			Pen.stroke;

			Pen.font_(Font(font.name,font.size - 2));
			Pen.fillColor = gridColor;
			Pen.strokeColor = gridColor;

			// draw vertical lines and units (avoid drawing lines and units outside bounds of the view)
			selGridLineCoord.x.inRange(0,me.bounds.width).if {
				// selGridLineCoord.x is in the visible part of the view
				this.prDrawVertGridLinesRight(selGridLineCoord.copy,me.bounds,remNumGridLines);
				this.prDrawVertGridLinesLeft(selGridLineCoord.copy - (vhorzGridDist@timeIncr),me.bounds,(remNumGridLines - 1).wrap(0,numGridLinesPerUnit - 1));
			} { var rem,pos,i;
				rem = remNumGridLines;
				pos = selGridLineCoord.copy;
				(selGridLineCoord.x < 0).if {
					// selGridLineCoord.x is to the left of the visible part of the view
					i = ((maxHorzGridDist.neg - pos.x)/vhorzGridDist).round(1).asInteger;
					pos.x = pos.x + (i*vhorzGridDist);
					pos.y = pos.y + (i*timeIncr);
					rem = (i + rem) % numGridLinesPerUnit;
					this.prDrawVertGridLinesRight(pos,me.bounds,rem);
				} {
					// selGridLineCoord.x is to the right of the visible part of the view
					i = ((pos.x - me.bounds.width - maxHorzGridDist)/vhorzGridDist).round(1).asInteger;
					pos.x = pos.x - (i*vhorzGridDist);
					pos.y = pos.y - (i*timeIncr);
					rem = (numGridLinesPerUnit - ((i - rem) % numGridLinesPerUnit)).wrap(0,numGridLinesPerUnit - 1);
					this.prDrawVertGridLinesLeft(pos,me.bounds,rem);
				}
			};

			Pen.stroke;

			// first draw line segments
			Pen.strokeColor = Color.grey(0.5); Pen.fillColor = Color.grey(1,0.5); Pen.width = 1;
			Pen.moveTo(envData.breakPointCoords[0].x@(me.bounds.height + 1));
			(1..envData.breakPointCoords.lastIndex) do: { |i|
				lineResolution do: { |j|
					var k = j/(lineResolution - 1);
					Pen.lineTo(
						(envData.breakPointCoords[i - 1].x + (k*(envData.breakPointCoords[i].x - envData.breakPointCoords[i - 1].x)))@(envData.breakPointCoords[i - 1].y + (env.curves[i - 1].asWarp.map(k)*(envData.breakPointCoords[i].y - envData.breakPointCoords[i - 1].y)))
					)
				}
			};
			Pen.lineTo(envData.breakPointCoords.last.x@(me.bounds.height + 1));
			Pen.lineTo(envData.breakPointCoords.first.x@(me.bounds.height + 1));
			Pen.fillStroke;

			// then draw breakpoints, curvepoints (if in visible part of the view)
			Pen.strokeColor = Color.red;
			envData.breakPointCoords do: { |brPtCoord,i|
				(i == loopStartNode or: { i == loopEndNode }).if {
					Pen.width = 0.25;
					Pen.moveTo(brPtCoord.x@0);
					Pen.lineTo(brPtCoord.x@me.bounds.height);
					(i == loopEndNode).if {
						Pen.moveTo(brPtCoord);
						Pen.lineTo((envData.breakPointCoords[loopStartNode].x - 2.5)@brPtCoord.y)
					};
					Pen.stroke
				};

				Pen.width = 0.5;
				Pen.fillColor = (envData.selBreakPoint == i
					or: { envData.selBreakPoint == 0 and: { i == envData.breakPointCoords.lastIndex } }
					or: { envData.selBreakPoint == envData.breakPointCoords.lastIndex and: { i == 0 } }
				).if { Color.red } { Color.white };

				brPtCoord.x.inRange(maxHorzGridDist.neg,me.bounds.width + maxHorzGridDist).if {
					Pen.addRect(Rect(brPtCoord.x - (breakPointSize/2),brPtCoord.y - (breakPointSize/2),breakPointSize,breakPointSize));
					(i > 0).if { Pen.addArc(envData.curvePointCoords[i - 1],curvePointRadius,0,2pi) }
				};

				(i > 0 and: { envData.curvePointCoords[i - 1].x.inRange(maxHorzGridDist.neg,me.bounds.width + maxHorzGridDist) }).if {
					Pen.addArc(envData.curvePointCoords[i - 1],curvePointRadius,0,2pi)
				};
				Pen.fillStroke
			}

		}).keyDownAction_({ |me,char,mod,key,uni|
			ctlKeyDown = mod.isCtrl
		}).keyUpAction_({ |me,char,mod,key,uni|
			ctlKeyDown = false
		}).mouseDownAction_({ |me,x,y,mod|
			var n,lim = breakPointSize/2;

			// calculate new selGridLineCoord
			(x != selGridLineCoord.x).if { this.prSelGridLineCoord_(this.prFindNearestGridCoord(x)) };
			// calculate new remNumGridLines (i.e. nr. of vertical grid lines to the left of a unit)
			remNumGridLines = this.prCalcRemNumGridLines;

			//[selGridLineCoord,remNumGridLines].postln;

			// check if position of mouse click is that of a break or curve point
			onBreakPoint = false;
			block { |break|
				envData.breakPointCoords do: { |bPtCoord,i|
					((bPtCoord.x - x).abs <= lim and: { (bPtCoord.y - y).abs <= lim }).if {
						envData.selBreakPoint = i;
						onBreakPoint = true;
						break.value
					}
				}
			};
			onCurvePoint = false;
			block { |break|
				envData.curvePointCoords do: { |cPtCoord,i|
					((cPtCoord.x - x).abs <= lim and: { (cPtCoord.y - y).abs <= lim }).if {
						envData.selBreakPoint = i + 1;
						onCurvePoint = true;
						break.value
					}
				}
			};

			(breakPointMode == \chained and: { onBreakPoint } and: { envData.selBreakPoint > 0 }).if {
				/*
				 * calculate difference between breakpoints starting from selected breakpoint,
				 * so breakpoints to the right of the selected breakpoint can be shifted horizontally
				 * relative to the position of itself
				 */
				dbreakPointXCoords = envData.breakPointCoords[envData.selBreakPoint..envData.breakPointCoords.lastIndex].performUnaryOp(\x).differentiate;
				dbreakPointXCoords.removeAt(0);
				initBreakPointTime = env.times[0..envData.selBreakPoint - 1].sum;
				dinitBreakPointTime = env.times[envData.selBreakPoint - 1]
			};

			ctlKeyDown.if {
				onBreakPoint.if {
					// delete break point except very first and don't delete any break points if there are only four left
					(envData.selBreakPoint != 0 and: { envData.breakPointCoords.size > 4 }).if { var tmp;

						// remove break point and associated curve point
						envData.breakPointCoords.removeAt(envData.selBreakPoint);
						envData.curvePointCoords.removeAt(envData.selBreakPoint - 1);
						tmp = env.levels.copy;
						tmp.removeAt(envData.selBreakPoint);
						env.levels = tmp;
						tmp = env.curves.copy;
						tmp.removeAt(envData.selBreakPoint - 1);
						env.curves = tmp;
						(envData.selBreakPoint - 1 == env.levels.lastIndex).if {
							tmp = env.times.copy;
							tmp.removeAt(envData.selBreakPoint - 1);
							env.times = tmp;
							envData.selBreakPoint = env.levels.lastIndex
						} {
							tmp = env.times.removeAt(envData.selBreakPoint - 1);
							env.setTime(envData.selBreakPoint - 1,env.times[envData.selBreakPoint - 1] + tmp);
							envData.calcXCurvePoint(envData.selBreakPoint - 1);
							envData.calcYCurvePoint(envData.selBreakPoint - 1)
						};

						/*
						 * delete break point distance of previous selected breakpoint to new selected break point in chained mode
						 * and when not the last break point
						 */
						(breakPointMode == \chained and: { envData.selBreakPoint < envData.breakPointCoords.lastIndex }).if {
							dbreakPointXCoords.removeAt(0)
						};

						// shift loop nodes according to position of new break point
						(envData.selBreakPoint == 1 and: { loopStartNode == 1 }).not.if {
							(envData.selBreakPoint <= loopStartNode).if { loopStartNode = loopStartNode - 1 }
						};
						(loopEndNode - loopStartNode > 1).if {
							(envData.selBreakPoint <= loopEndNode).if { loopEndNode = loopEndNode - 1 }
						}
					}
				} { var insertInd,breakPointCoordX,breakPointTime;
					#breakPointCoordX,breakPointTime = switch(unitMode,
						\time, { (x@((x - selGridLineCoord.x)*timeStep/(vhorzGridDist*numGridLinesPerUnit) + selGridLineCoord.y)).asArray },
						\tempo, { this.prFindNearestGridCoord(x).asArray }
					);

					// insert break point and associated curve point and select it
					insertInd = envData.breakPointCoords.performUnaryOp(\x).indexOfGreaterThan(breakPointCoordX) ? envData.breakPointCoords.size;
					env.levels = env.levels.insert(insertInd,y.linlin(0,me.bounds.height,maxRange,minRange));
					env.times = (insertInd == 1).if {
						env.times.insert(0,breakPointTime)
					} {
						env.times.insert(insertInd - 1,breakPointTime - env.times[0..insertInd - 2].sum)
					};
					(insertInd < env.times.lastIndex).if {
						env.setTime(insertInd,env.times[insertInd] - env.times[insertInd - 1])
					};
					envData.breakPointCoords = envData.breakPointCoords.insert(insertInd,breakPointCoordX@y);
					envData.curvePointCoords = envData.curvePointCoords.insert(insertInd - 1,(envData.breakPointCoords[insertInd] + envData.breakPointCoords[insertInd - 1])/2);
					env.curves = env.curves.insert(insertInd - 1,0);
					(insertInd < envData.breakPointCoords.lastIndex).if {
						envData.calcXCurvePoint(insertInd);
						envData.calcYCurvePoint(insertInd)
					};

					// if inserted break point is the last, update break and curve point data for first break point
					(insertInd == env.levels.lastIndex).if {
						env.setLevel(0,env.levels[insertInd]);
						envData.breakPointCoords[0].y = envData.breakPointCoords[insertInd].y;
						envData.calcYCurvePoint(0)
					};

					// if in chained mode recalculate break point distance
					dbreakPointXCoords = envData.breakPointCoords[insertInd..envData.breakPointCoords.lastIndex].performUnaryOp(\x).differentiate;
					dbreakPointXCoords.removeAt(0);

					// shift loop nodes according to position of new break point
					(insertInd <= loopStartNode).if { loopStartNode = loopStartNode + 1 };
					(insertInd <= loopEndNode).if { loopEndNode = loopEndNode + 1 };

					envData.selBreakPoint = insertInd
				}
			};

			prevPoint = x@y;
			me.refresh
		}).mouseMoveAction_({ |me,x,y,mod|
			var dx = (x - prevPoint.x).clip(-10,10),dy = y - prevPoint.y,vleftNumGridLines,currZeroPos,breakPointLevel,breakPointCoordX,breakPointTime,prevBreakPointTime,nextBreakPointTime;

			onBreakPoint.if {
				breakPointLevel = y.linlin(0,me.bounds.height,maxRange,minRange);

				(envData.selBreakPoint == 0).if {
					// first break point is only allowed to move up and down
					env.setLevel(0,breakPointLevel);
					envData.breakPointCoords[0].y = y.clip(0,me.bounds.height);
					env.setLevel(env.levels.lastIndex,env.levels[0]);
					envData.breakPointCoords[envData.breakPointCoords.lastIndex].y = envData.breakPointCoords[0].y;

					// adjust first and last curve points
					envData.calcYCurvePoint(0);
					envData.calcYCurvePoint(envData.curvePointCoords.lastIndex)
				} {
					// calculate absolute times of selected break point and the break point preceding it
					prevBreakPointTime = env.times[0..envData.selBreakPoint - 2].sum;
					#breakPointCoordX,breakPointTime = switch(unitMode,
						\time, { (x@((x - selGridLineCoord.x)*timeStep/(vhorzGridDist*numGridLinesPerUnit) + selGridLineCoord.y)).asArray },
						\tempo, { this.prFindNearestGridCoord(x).asArray }
					);

					/*
					 * if selected break point is the last, allow it to move up, down and to the right freely
					 * but not past preceding break point and also move first break point up and down
					 */
					(envData.selBreakPoint == env.levels.lastIndex).if {
						env.setLevel(envData.selBreakPoint,breakPointLevel);
						breakPointTime = breakPointTime.max(prevBreakPointTime);
						env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
						envData.breakPointCoords[envData.selBreakPoint] = breakPointCoordX.max(envData.breakPointCoords[envData.selBreakPoint - 1].x)@y.clip(0,me.bounds.height);
						env.setLevel(0,breakPointLevel);
						envData.breakPointCoords[0].y = y.clip(0,me.bounds.height);

						// adjust first and last curve points
						envData.calcYCurvePoint(0);
						envData.calcXCurvePoint(envData.curvePointCoords.lastIndex);
						envData.calcYCurvePoint(envData.curvePointCoords.lastIndex)
					} {
						(envData.breakPointCoords[envData.selBreakPoint].x > envData.breakPointCoords[envData.selBreakPoint - 1].x and:
							{ envData.breakPointCoords[envData.selBreakPoint].x < envData.breakPointCoords[envData.selBreakPoint + 1].x }).if {
							/*
							 * if x-coordinate of break point is in between the x-coordinate of preceding break point
							 * and that of the next break point we can move the break point to any position
							 */
							env.setLevel(envData.selBreakPoint,breakPointLevel);
							env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
							envData.breakPointCoords[envData.selBreakPoint] = breakPointCoordX@y.clip(0,me.bounds.height)
						} {
							(envData.breakPointCoords[envData.selBreakPoint].x <= envData.breakPointCoords[envData.selBreakPoint - 1].x).if {
								/*
							   	 * if x-coordinate of selected break point is equal to the x-coordinate of the preceding break point,
							 	 * allow it to move up and down but not past preceding break point
							 	 */
								env.setLevel(envData.selBreakPoint,breakPointLevel);
								breakPointTime = breakPointTime.max(prevBreakPointTime);
								env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
								envData.breakPointCoords[envData.selBreakPoint] = breakPointCoordX.max(envData.breakPointCoords[envData.selBreakPoint - 1].x)@y.clip(0,me.bounds.height)
							} {
								/*
								 * if the x-coordinate of the break point is equal to the x-coordinate of the next break point,
								 * allow it to move up and down but not past the next break point when in loose mode
								 */
								env.setLevel(envData.selBreakPoint,breakPointLevel);
								nextBreakPointTime = env.times[0..envData.selBreakPoint].sum;
								breakPointTime = breakPointTime.min(nextBreakPointTime);
								env.setTime(envData.selBreakPoint - 1,breakPointTime - prevBreakPointTime);
								envData.breakPointCoords[envData.selBreakPoint] = breakPointCoordX.min(envData.breakPointCoords[envData.selBreakPoint + 1].x)@y.clip(0,me.bounds.height)
							}
						};

						// adjust curve points to the left and right of the break point
						envData.calcXCurvePoint(envData.selBreakPoint - 1);
						envData.calcYCurvePoint(envData.selBreakPoint - 1);
						envData.calcXCurvePoint(envData.selBreakPoint);
						envData.calcYCurvePoint(envData.selBreakPoint);

						/*
						 * if in chained break point mode, shift all break points and curve points to the right of
						 * the selected break point an equal amount in the horizontal direction
						 */
						(breakPointMode == \chained).if {
							env.setTime(envData.selBreakPoint - 1,(dinitBreakPointTime + breakPointTime - initBreakPointTime).max(0));
							(envData.selBreakPoint + 1..envData.breakPointCoords.lastIndex) do: { |i,j|
								envData.breakPointCoords[i].x = envData.breakPointCoords[i - 1].x + dbreakPointXCoords[j];
								(j > 0).if {
									envData.curvePointCoords[i - 1].x = (envData.breakPointCoords[i - 1].x + envData.breakPointCoords[i].x)/2
								}
							}
						}
					}
				}
			} {
				onCurvePoint.if {
					envData.calcYCurvePoint(envData.selBreakPoint - 1,dy)
				} {
					// if the mouse cursor is not on a selected break point or a curve point, scale or translate the view

					// translate view
					this.prSelGridLineCoordX_(selGridLineCoord.x + dx);

					// zoom in / out at currently selected grid line
					(scaleRespCount == 0).if {

						(dy < 0 and: { uI < (unitStep[\time].lastIndex - 1) } or: { dy > 0 and: { uI > 1 } }).if {
							this.prVhorzGridDist_(vhorzGridDist - dy)
						};

						(dy < 0).if {
							// mouse is dragged up -> zoom in
							(vhorzGridDist > maxHorzGridDist).if {
								this.prVhorzGridDist_(minHorzGridDist);
								uI = uI + 1;
								this.prTimeStep_(
									switch(unitMode,
										\time, { unitStep[\time][uI] },
										\tempo, { unitStep[\tempo][uI][\time] }
									)
								);
								timeIncr = timeStep/numGridLinesPerUnit;
								remNumGridLines = this.prCalcRemNumGridLines
							}
						};
						(dy > 0).if {
							// mouse is dragged down -> zoom out
							(vhorzGridDist < minHorzGridDist).if {
								this.prVhorzGridDist_(maxHorzGridDist);
								uI = uI - 1;
								this.prTimeStep_(
									switch(unitMode,
										\time, { unitStep[\time][uI] },
										\tempo, { unitStep[\tempo][uI][\time] }
									)
								);
								timeIncr = timeStep/numGridLinesPerUnit;
								remNumGridLines = this.prCalcRemNumGridLines;
								// quantize previous selected grid coordinate to nearest current selected grid coordinate
								this.prSelGridLineCoordY_((selGridLineCoord.y/timeStep).asInteger*timeStep + (remNumGridLines*timeIncr))
							}
						}
					};

					// lock left side of view to 0
					(domainMode == \unipolar).if { this.prAdjustZeroPos };

					// rescale envelope when grid is rescaled
					envData.updateEnvPlotData
				}

			};

			scaleRespCount = scaleRespCount + 1;
			(scaleRespCount >= scaleResponsiveness).if { scaleRespCount = 0 };
			prevPoint = x@y;
			me.refresh
		});

		settingsView = View().layout_(
			HLayout(
				[
					NumberBox().value_(0).minWidth_(42).maxWidth_(46).align_(\right).action_({ arg number;
						(number.value > maxRange).if {
							minRange = maxRange;
							maxRange = number.value
						};
						minRange = number.value;
						rangeView.refresh
					}),s:1
				],
				StaticText().string_("minimum").font_(font),
				[
					NumberBox().value_(1).minWidth_(42).maxWidth_(46).align_(\right).action_({ arg number;
						(number.value < minRange).if {
							maxRange = minRange;
							minRange = number.value
						};
						maxRange = number.value;
						rangeView.refresh
					}),s:1
				],
				StaticText().string_("maximum").font_(font),
				nil,
				PopUpMenu().items_(["time","tempo"]).font_(font).action_({ arg menu;
					unitMode = menu.item.asSymbol;
					switch(unitMode,
						\time, { this.prTimeStep_(unitStep[\time][uI]) },
						\tempo, { this.prTimeStep_(unitStep[\tempo][uI][\time]) }
					);
					timeIncr = timeStep/numGridLinesPerUnit;
					this.prSelGridLineCoord_(this.prFindNearestGridCoord(selGridLineCoord.x));
					remNumGridLines = this.prCalcRemNumGridLines;
					(domainMode == \unipolar).if { this.prAdjustZeroPos };
					envData.updateEnvPlotData;
					envView.refresh
				}).valueAction_(0),
				brPtModeMenu = PopUpMenu().items_(["chained","loose"]).font_(font).action_({ arg menu;
					breakPointMode = menu.item.asSymbol
				}).valueAction_(0)
			).margins_(0)
		);

		view.layout_(
			GridLayout.rows(
				[nil,settingsView],
				[[rangeView,rows:3]],
				[nil,envView]
			).hSpacing_(0).setColumnStretch(0,1).setColumnStretch(1,20).setRowStretch(0,5).setRowStretch(1,1).setRowStretch(2,74).setRowStretch(3,1)
		)

	}

	background_ { arg newBackground,refreshFlag = true;
		envView.background_(newBackground);
		refreshFlag.if { envView.refresh }
	}

	horzGridDist_ { arg newHorzGridDist,refreshFlag = true;
		horzGridDist = newHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	maxHorzGridDist_ { arg newMaxHorzGridDist,refreshFlag = true;
		maxHorzGridDist = newMaxHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	minHorzGridDist_ { arg newMinHorzGridDist,refreshFlag = true;
		minHorzGridDist = newMinHorzGridDist;
		refreshFlag.if { envView.refresh }
	}

	numVertGridLines_ { arg newNumVertGridLines,refreshFlag = true;
		numVertGridLines = newNumVertGridLines;
		refreshFlag.if { envView.refresh }
	}

	gridColor_ { arg newGridColor,refreshFlag = true;
		gridColor = newGridColor;
		refreshFlag.if { envView.refresh }
	}

	gridWidth_ { arg newGridWidth,refreshFlag = true;
		gridWidth = newGridWidth;
		refreshFlag.if { envView.refresh }
	}

	showHorzAxis_ { arg newShowHorzAxis,refreshFlag = true;
		showHorzAxis = newShowHorzAxis;
		refreshFlag.if { envView.refresh }
	}

	showVertAxis_ { arg newShowVertAxis,refreshFlag = true;
		showVertAxis = newShowVertAxis;
		refreshFlag.if { envView.refresh }
	}

	breakPointSize_ { arg newBreakPointSize,refreshFlag = true;
		breakPointSize = newBreakPointSize;
		refreshFlag.if { envView.refresh }
	}

	curvePointRadius_ { arg newCurvePointRadius,refreshFlag = true;
		curvePointRadius = newCurvePointRadius;
		refreshFlag.if { envView.refresh }
	}

	font_ { arg newFont,refreshFlag = true;
		font = newFont;
		refreshFlag.if { settingsView.refresh; rangeView.refresh; envView.refresh }
	}

	unitMode_ { arg newUnitMode,refreshFlag = true;
		unitMode = newUnitMode;
		refreshFlag.if { envView.refresh }
	}

	domainMode_ { arg newDomainMode,refreshFlag = true;
		#[\unipolar,\bipolar].includes(newDomainMode).if {
			domainMode = newDomainMode;
			refreshFlag.if { envView.refresh }
		} {
			"domainMode is not a valid choice".warn
		}
	}

	minRange_ { arg newMinRange,refreshFlag = true;
		minRange = newMinRange;
		envData.minLevel = newMinRange;
		refreshFlag.if { rangeView.refresh }
	}

	maxRange_ { arg newMaxRange,refreshFlag = true;
		maxRange = newMaxRange;
		envData.maxLevel = newMaxRange;
		refreshFlag.if { rangeView.refresh }
	}

	scaleResponsiveness_ { arg newScaleResponsiveness;
		scaleResponsiveness = newScaleResponsiveness.asInteger.max(1)
	}

	env_ { arg newEnv,refreshFlag = true;
		newEnv.isKindOf(Env).if {
			env = newEnv;
			envData = EnvPlotData(env);
			refreshFlag.if { envView.refresh }
		} {
			Error("arg env has to be an instance of %\n".format(Env.name)).throw
		}
	}

	drawFunc_ { arg newDrawFunc,refreshFlag = true;
		drawFunc = newDrawFunc;
		refreshFlag.if { envView.refresh }
	}

	// private setters
	prTimeStep_ { arg newTimeStep;
		timeStep = newTimeStep;
		envData.timeStep = newTimeStep
	}

	prSelGridLineCoord_ { arg newSelGridLineCoord;
		selGridLineCoord = newSelGridLineCoord;
		envData.refCoord = newSelGridLineCoord
	}

	prSelGridLineCoordX_ { arg newSelGridLineCoordX;
		selGridLineCoord.x = newSelGridLineCoordX;
		envData.refCoord.x = newSelGridLineCoordX
	}

	prSelGridLineCoordY_ { arg newSelGridLineCoordY;
		selGridLineCoord.y = newSelGridLineCoordY;
		envData.refCoord.y = newSelGridLineCoordY
	}

	prVhorzGridDist_ { arg newVhorzGridDist;
		vhorzGridDist = newVhorzGridDist;
		envData.unitDist = newVhorzGridDist*numGridLinesPerUnit
	}

	// private methods
	prFindNearestGridCoord { arg x;
		var n = ((x - selGridLineCoord.x)/vhorzGridDist).round(1);
		^((n*vhorzGridDist + selGridLineCoord.x)@(n*timeIncr + selGridLineCoord.y).round(timeIncr))
	}

	prCalcRemNumGridLines {
		^((selGridLineCoord.y - selGridLineCoord.y.trunc(timeStep))/timeIncr).round(1).asInteger;
	}

	prDrawVertGridLinesRight { arg pos,bounds,rem;
		while({ pos.x <= bounds.width }) {
			Pen.moveTo(pos.x@0);
			Pen.lineTo(pos.x@bounds.height);
			(rem == 0).if {
				var str = switch(unitMode,
					\time, { this.prMakeTimeStr(pos.y.round(timeStep)) },
					\tempo, { this.prMakeTempoStr(pos.y.round(timeStep)) }
				);
				Pen.stringAtPoint(str,pos.x@(bounds.height/2 - 5))
			};
			pos.x = pos.x + vhorzGridDist;
			pos.y = pos.y + timeIncr;
			rem = rem + 1;
			(rem == numGridLinesPerUnit).if { rem = 0 }
		}
	}

	prDrawVertGridLinesLeft { arg pos,bounds,rem;
		while({ pos.x >= maxHorzGridDist.neg }) {
			Pen.moveTo(pos.x@0);
			Pen.lineTo(pos.x@bounds.height);
			(rem == 0).if {
				var str = switch(unitMode,
					\time, { this.prMakeTimeStr(pos.y.round(timeStep)) },
					\tempo, { this.prMakeTempoStr(pos.y.round(timeStep)) }
				);
				Pen.stringAtPoint(str,pos.x@(bounds.height/2 - 5))
			};
			pos.x = pos.x - vhorzGridDist;
			pos.y = pos.y - timeIncr;
			rem = rem - 1;
			(rem < 0).if { rem = numGridLinesPerUnit - 1 }
		}
	}

	prAdjustZeroPos {
		var currZeroPos = (selGridLineCoord.x - (selGridLineCoord.y/timeIncr*vhorzGridDist)).round(1);
		(currZeroPos > 0).if { this.prSelGridLineCoordX_(selGridLineCoord.x - currZeroPos) }
	}

	prMakeTimeStr { arg number,prec = 4;
		var str = number.asFloat.asStringPrec(prec);
		(number.frac < 1e-9).if { str = str ++ ".0" };
		^str
	}

	prMakeTempoStr { arg number;
		var numb = number.asFraction,measure = unitStep[\tempo][uI][\measure];
		(numb[1] >= numb[0] and: { numb[1] != measure[1].div(2) }).if {
			numb = ((measure[1].div(2)/numb[1])*numb).round(1).asInteger
		};
		numb[1] = numb[1]*2;
		^((number.abs < 1e-9).if { "" } { numb[0].asString ++ "/" ++ numb[1].asString })
	}

}

EnvPlotData {
	var <>env,<>breakPointCoords,<>curvePointCoords,<>timeStep,<>refCoord,<>height,<>unitDist,<>minLevel,<>maxLevel,<>selBreakPoint;

	*new { arg env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint;
		^super.new.init(env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint)
	}

	init { arg env,timeStep,refCoord,height,minLevel,maxLevel,unitDist,selBreakPoint;
		this.env = env ? Env([0,1,1,0],[0,0.5,0],[0,0,0],nil,nil);
		this.timeStep = timeStep ? 0.2;
		this.refCoord = refCoord ? 320@0.4;
		this.height = height ? 300;
		this.unitDist = unitDist ? 100;
		this.minLevel = minLevel ? this.env.levels.minItem;
		this.maxLevel = maxLevel ? this.env.levels.maxItem;
		this.selBreakPoint = selBreakPoint ? 1;
		/*this.updateAllBreakPointCoords;
		this.updateAllCurvePointCoords;*/
	}

	createAllBreakPointCoords {
		var xc,yc;
		yc = env.levels collect: _.linlin(minLevel,maxLevel,height,0);
		xc = [0] ++ env.times.integrate collect: { |t|
			(t == refCoord.y).if {
				refCoord.x
			} {
				refCoord.x - ((refCoord.y - t)/timeStep*unitDist).round(1)
			}
		};
		breakPointCoords = [xc,yc].flop collect: _.asPoint
	}

	updateAllBreakPointCoordsX {
		[0] ++ env.times.integrate do: { |t,i|
			breakPointCoords[i].x = (t == refCoord.y).if { refCoord.x } { refCoord.x - ((refCoord.y - t)/timeStep*unitDist).round(1) }
		}
	}

	updateAllBreakPointCoordsY {
		env.levels do: { |lvl,i|
			breakPointCoords[i].y = lvl.linlin(minLevel,maxLevel,height,0)
		}
	}

	updateAllBreakPointCoords {
		[0] ++ env.times.integrate do: { |t,i|
			breakPointCoords[i].x = (t == refCoord.y).if { refCoord.x } { refCoord.x - ((refCoord.y - t)/timeStep*unitDist).round(1) };
			breakPointCoords[i].y = env.levels[i].linlin(minLevel,maxLevel,height,0)
		}
	}

	createAllCurvePointCoords {
		curvePointCoords = { Point() } ! (breakPointCoords.size - 1);
		 (breakPointCoords.size - 1) do: { |i|
			this.calcXCurvePoint(i);
			this.calcYCurvePoint(i)
		}
	}

	updateAllCurvePointCoords {
		 (breakPointCoords.size - 1) do: { |i|
			this.calcXCurvePoint(i);
			this.calcYCurvePoint(i)
		}
	}

	updateAllCurvePointCoordsY {
		 (breakPointCoords.size - 1) do: { |i|
			this.calcYCurvePoint(i)
		}
	}

	createEnvPlotData {
		this.createAllBreakPointCoords;
		this.createAllCurvePointCoords
	}

	updateEnvPlotData {
		this.updateAllBreakPointCoords;
		this.updateAllCurvePointCoords
	}

	calcXCurvePoint { arg idx;
		curvePointCoords[idx].x = (breakPointCoords[idx].x + breakPointCoords[idx + 1].x)/2
	}

	calcYCurvePoint { arg idx,inc;
		(breakPointCoords[idx + 1].y > breakPointCoords[idx].y).if {
			inc.notNil.if {
				env.setCurve(idx,(env.curves[idx] - (inc.sign*0.25)).clip(-18,18))
			};
			curvePointCoords[idx].y = breakPointCoords[idx].y - (env.curves[idx].asWarp.map(0.5)*(breakPointCoords[idx].y - breakPointCoords[idx + 1].y))
		} {
			inc.notNil.if {
				env.setCurve(idx,(env.curves[idx] + (inc.sign*0.25)).clip(-18,18))
			};
			curvePointCoords[idx].y = breakPointCoords[idx + 1].y - (env.curves[idx].neg.asWarp.map(0.5)*(breakPointCoords[idx + 1].y - breakPointCoords[idx].y))
		}
	}

}

// hack to ensure envelope state is properly updated
+ Env {

	setLevel { arg idx,value;
		(idx == 0).if {
			this.levels = [value] ++ this.levels[idx + 1.. this.levels.lastIndex]
		} {
			(idx == this.levels.lastIndex).if {
				this.levels = this.levels[0..idx - 1] ++ [value]
			} {
				this.levels = this.levels[0..idx - 1] ++ [value] ++ this.levels[idx + 1.. this.levels.lastIndex]
			}
		}
	}

	setTime { arg idx,value;
		(idx == 0).if {
			this.times = [value] ++ this.times[idx + 1.. this.times.lastIndex]
		} {
			(idx == this.times.lastIndex).if {
				this.times = this.times[0..idx - 1] ++ [value]
			} {
				this.times = this.times[0..idx - 1] ++ [value] ++ this.times[idx + 1.. this.times.lastIndex]
			}
		}
	}

	setCurve { arg idx,value;
		(idx == 0).if {
			this.curves = [value] ++ this.curves[idx + 1.. this.curves.lastIndex]
		} {
			(idx == this.curves.lastIndex).if {
				this.curves = this.curves[0..idx - 1] ++ [value]
			} {
				this.curves = this.curves[0..idx - 1] ++ [value] ++ this.curves[idx + 1.. this.curves.lastIndex]
			}
		}
	}

}